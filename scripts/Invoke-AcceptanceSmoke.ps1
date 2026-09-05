param(
    [string]$GatewayBaseUrl = 'http://localhost:6200',
    [string]$LoyaltyBaseUrl = 'http://localhost:6204',
    [string]$OrderServiceBaseUrl = 'http://localhost:6206',
    [string]$InternalApiKey = $env:INTERNAL_API_KEY,
    [int]$TimeoutSeconds = 30
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    function Get-DotEnvValue([string]$Name) {
        $envPath = Join-Path $repoRoot '.env'
        if (-not (Test-Path -LiteralPath $envPath)) { return $null }

        $line = Get-Content -LiteralPath $envPath |
            Where-Object { $_ -match "^\s*$([Regex]::Escape($Name))\s*=" } |
            Select-Object -First 1
        if (-not $line) { return $null }

        return ($line -replace "^\s*$([Regex]::Escape($Name))\s*=\s*", '').Trim().Trim('"')
    }

    if ([string]::IsNullOrWhiteSpace($InternalApiKey)) {
        $InternalApiKey = Get-DotEnvValue 'INTERNAL_API_KEY'
    }

    if ([string]::IsNullOrWhiteSpace($InternalApiKey)) {
        throw 'Missing INTERNAL_API_KEY. Copy .env.example to .env before running this script.'
    }

    function Write-Step([string]$Message) {
        Write-Host "[test] $Message" -ForegroundColor Cyan
    }

    function Write-Pass([string]$Message) {
        Write-Host "[pass] $Message" -ForegroundColor Green
    }

    function ConvertTo-RequestJson($Body) {
        if ($null -eq $Body) { return $null }
        return $Body | ConvertTo-Json -Depth 10 -Compress
    }

    function Invoke-JsonRequest {
        param(
            [Parameter(Mandatory)] [string]$Method,
            [Parameter(Mandatory)] [string]$Uri,
            [hashtable]$Headers = @{},
            $Body = $null
        )

        $parameters = @{
            Method = $Method
            Uri = $Uri
            Headers = $Headers
            TimeoutSec = $TimeoutSeconds
        }

        if ($null -ne $Body) {
            $parameters.ContentType = 'application/json'
            $parameters.Body = ConvertTo-RequestJson $Body
        }

        Invoke-RestMethod @parameters
    }

    function Assert-Equal($Actual, $Expected, [string]$Name) {
        if ($Actual -ne $Expected) {
            throw "$Name failed. Expected [$Expected], actual [$Actual]."
        }
        Write-Pass $Name
    }

    function Assert-True($Condition, [string]$Name) {
        if (-not $Condition) {
            throw "$Name failed."
        }
        Write-Pass $Name
    }

    function Invoke-ExpectedError {
        param(
            [Parameter(Mandatory)] [string]$Method,
            [Parameter(Mandatory)] [string]$Uri,
            [hashtable]$Headers = @{},
            $Body = $null,
            [Parameter(Mandatory)] [int]$ExpectedStatus,
            [Parameter(Mandatory)] [string]$ExpectedCode
        )

        try {
            Invoke-JsonRequest -Method $Method -Uri $Uri -Headers $Headers -Body $Body | Out-Null
            throw "Expected HTTP $ExpectedStatus with code $ExpectedCode, but request succeeded."
        }
        catch {
            $statusCode = $null
            $content = $_.ErrorDetails.Message

            if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
                $statusCode = [int]$_.Exception.Response.StatusCode
            }

            if ([string]::IsNullOrWhiteSpace($content) -and $_.Exception.Response) {
                $stream = $_.Exception.Response.GetResponseStream()
                if ($stream) {
                    $reader = [System.IO.StreamReader]::new($stream)
                    try { $content = $reader.ReadToEnd() }
                    finally { $reader.Dispose() }
                }
            }

            if ($statusCode -ne $ExpectedStatus) {
                throw "Expected HTTP $ExpectedStatus, actual [$statusCode]. Body: $content"
            }

            $json = $content | ConvertFrom-Json
            if ($json.code -ne $ExpectedCode) {
                throw "Expected error code $ExpectedCode, actual [$($json.code)]. Body: $content"
            }

            Write-Pass "$Method $Uri returns $ExpectedStatus/$ExpectedCode"
        }
    }

    function Wait-Until {
        param(
            [Parameter(Mandatory)] [string]$Name,
            [Parameter(Mandatory)] [scriptblock]$Condition,
            [int]$Seconds = 30
        )

        $deadline = (Get-Date).AddSeconds($Seconds)
        do {
            if (& $Condition) {
                Write-Pass $Name
                return
            }
            Start-Sleep -Milliseconds 700
        } while ((Get-Date) -lt $deadline)

        throw "Timed out waiting for $Name."
    }

    function Get-LoyaltyMe([hashtable]$Headers) {
        Invoke-JsonRequest -Method Get -Uri "$GatewayBaseUrl/api/loyalty/me" -Headers $Headers
    }

    function Set-OrderStatus($Order, [string]$Status, [Guid]$DriverId) {
        $body = @{
            status = $Status
            version = [int]$Order.version
            driverId = $DriverId
        }
        Invoke-JsonRequest `
            -Method Patch `
            -Uri "$OrderServiceBaseUrl/api/order/$($Order.orderId)/status" `
            -Headers $internalHeaders `
            -Body $body
    }

    Write-Step 'Checking Gateway readiness'
    $ready = Invoke-JsonRequest -Method Get -Uri "$GatewayBaseUrl/health/ready"
    Assert-Equal $ready.status 'Ready' 'Gateway health is Ready'

    foreach ($dependency in @('identity', 'delivery', 'driver', 'loyalty', 'map', 'order', 'promotion')) {
        Assert-Equal $ready.dependencies.$dependency 'Ready' "$dependency health is Ready"
    }

    $customerId = [Guid]::NewGuid()
    $driverId = [Guid]::Parse('10000000-0000-0000-0000-000000000001')
    $token = & (Join-Path $PSScriptRoot 'New-DevJwt.ps1') -Role CUSTOMER -SubjectId $customerId
    $headers = @{
        Authorization = "Bearer $token"
        'X-Correlation-ID' = "smoke-$customerId"
    }
    $internalHeaders = @{ 'X-Internal-Key' = $InternalApiKey }

    Write-Step 'Checking public Loyalty API through Gateway'
    $me = Get-LoyaltyMe $headers
    Assert-Equal $me.customerId $customerId 'GET /api/loyalty/me uses customerId from JWT'
    Assert-Equal $me.availablePoints 0 'new customer starts with zero available points'
    Assert-Equal $me.reservedPoints 0 'new customer starts with zero reserved points'

    Write-Step 'Seeding points through internal Loyalty API'
    $seedOrderId = [Guid]::NewGuid()
    $seed = Invoke-JsonRequest `
        -Method Post `
        -Uri "$LoyaltyBaseUrl/api/loyalty/earn" `
        -Headers $internalHeaders `
        -Body @{ customerId = $customerId; orderId = $seedOrderId; orderAmount = 1000000 }
    Assert-Equal $seed.earnedPoints 100 'internal earn grants 100 points for 1,000,000 VND'

    $me = Get-LoyaltyMe $headers
    Assert-Equal $me.availablePoints 100 'points are visible through Gateway after internal earn'

    $preview = Invoke-JsonRequest `
        -Method Post `
        -Uri "$GatewayBaseUrl/api/loyalty/preview" `
        -Headers $headers `
        -Body @{ points = 5 }
    Assert-Equal $preview.discountAmount 5000 'preview converts 5 points to 5,000 VND'
    Assert-Equal $preview.availablePoints 100 'preview does not subtract points'

    Invoke-ExpectedError `
        -Method Post `
        -Uri "$GatewayBaseUrl/api/loyalty/preview" `
        -Headers $headers `
        -Body @{ points = 999999 } `
        -ExpectedStatus 409 `
        -ExpectedCode 'INSUFFICIENT_POINTS'

    Write-Step 'Checking direct reserve/release on LoyaltyService'
    $releaseReservation = Invoke-JsonRequest `
        -Method Post `
        -Uri "$LoyaltyBaseUrl/api/loyalty/reserve" `
        -Headers $internalHeaders `
        -Body @{ customerId = $customerId; orderId = [Guid]::NewGuid(); points = 8; orderAmount = 100000 }
    Assert-Equal $releaseReservation.discountAmount 8000 'reserve calculates point discount'

    $me = Get-LoyaltyMe $headers
    Assert-Equal $me.availablePoints 92 'reserve subtracts available points'
    Assert-Equal $me.reservedPoints 8 'reserve adds reserved points'

    Invoke-JsonRequest `
        -Method Post `
        -Uri "$LoyaltyBaseUrl/api/loyalty/reservations/$($releaseReservation.reservationId)/release" `
        -Headers $internalHeaders | Out-Null

    $me = Get-LoyaltyMe $headers
    Assert-Equal $me.availablePoints 100 'release returns points to available balance'
    Assert-Equal $me.reservedPoints 0 'release clears reserved balance'

    Write-Step 'Creating an order through Gateway with pointsToUse'
    $orderBody = @{
        pickupAddress = '1 Le Loi, Quan 1'
        dropoffAddress = '10 Nguyen Hue, Quan 1'
        pickupLocation = @{ latitude = 10.7769; longitude = 106.7009 }
        dropoffLocation = @{ latitude = 10.7731; longitude = 106.7041 }
        receiverPhone = '0900000000'
        promotionCode = $null
        pointsToUse = 5
        clientRequestId = [Guid]::NewGuid()
    }

    $order = Invoke-JsonRequest `
        -Method Post `
        -Uri "$GatewayBaseUrl/api/order" `
        -Headers $headers `
        -Body $orderBody

    Assert-Equal $order.status 'PENDING' 'created order starts as PENDING'
    Assert-Equal $order.discountPoints 5000 'OrderService reserves Loyalty points during create order'

    $me = Get-LoyaltyMe $headers
    Assert-Equal $me.availablePoints 95 'order reserve subtracts 5 available points'
    Assert-Equal $me.reservedPoints 5 'order reserve holds 5 points'

    Write-Step 'Moving order through internal statuses to commit points and earn points'
    $order = Set-OrderStatus -Order $order -Status 'ASSIGNED' -DriverId $driverId
    $order = Set-OrderStatus -Order $order -Status 'PICKED_UP' -DriverId $driverId

    Wait-Until -Name 'PICKED_UP commits reserved Loyalty points' -Seconds 30 -Condition {
        $current = Get-LoyaltyMe $headers
        ($current.availablePoints -eq 95) -and ($current.reservedPoints -eq 0)
    }

    $order = Set-OrderStatus -Order $order -Status 'DELIVERING' -DriverId $driverId
    $order = Set-OrderStatus -Order $order -Status 'COMPLETED' -DriverId $driverId
    $expectedEarnedPoints = [int][Math]::Floor([double]$order.totalFee / 10000)

    Wait-Until -Name 'COMPLETED earns Loyalty points once' -Seconds 30 -Condition {
        $current = Get-LoyaltyMe $headers
        $current.availablePoints -eq (95 + $expectedEarnedPoints)
    }

    $beforeDuplicate = Get-LoyaltyMe $headers
    $duplicateEarn = Invoke-JsonRequest `
        -Method Post `
        -Uri "$LoyaltyBaseUrl/api/loyalty/earn" `
        -Headers $internalHeaders `
        -Body @{ customerId = $customerId; orderId = $order.orderId; orderAmount = [int]$order.totalFee }
    $afterDuplicate = Get-LoyaltyMe $headers

    Assert-Equal $duplicateEarn.earnedPoints $expectedEarnedPoints 'duplicate earn returns the original earned points'
    Assert-Equal $afterDuplicate.availablePoints $beforeDuplicate.availablePoints 'duplicate earn does not add points twice'

    Write-Step 'Checking Gateway blocks internal endpoints'
    Invoke-ExpectedError `
        -Method Post `
        -Uri "$GatewayBaseUrl/api/loyalty/earn" `
        -Headers $internalHeaders `
        -Body @{ customerId = $customerId; orderId = [Guid]::NewGuid(); orderAmount = 100000 } `
        -ExpectedStatus 404 `
        -ExpectedCode 'INTERNAL_ENDPOINT_NOT_EXPOSED'

    Invoke-ExpectedError `
        -Method Patch `
        -Uri "$GatewayBaseUrl/api/order/$($order.orderId)/status" `
        -Headers $internalHeaders `
        -Body @{ status = 'COMPLETED'; version = $order.version; driverId = $driverId } `
        -ExpectedStatus 404 `
        -ExpectedCode 'INTERNAL_ENDPOINT_NOT_EXPOSED'

    Write-Host ''
    Write-Host 'Acceptance smoke test passed.' -ForegroundColor Green
}
finally {
    Pop-Location
}