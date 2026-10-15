param([string]$BaseUrl = 'http://localhost:6200')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$config = @{}
Get-Content (Join-Path $root '.env') | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.*)$') { $config[$matches[1]] = $matches[2].Trim('"') }
}
$internal = @{'X-Internal-Key' = $config['INTERNAL_API_KEY']}
$results = [System.Collections.Generic.List[string]]::new()
function Assert-Check([bool]$Condition, [string]$Name) {
    if (!$Condition) { throw "FAIL: $Name" }
    $results.Add($Name)
    Write-Output "PASS: $Name"
}
function Api([string]$Method, [string]$Path, $Body = $null, $Headers = @{}, [int]$Expected = 200) {
    $url = if ($Path.StartsWith('http')) { $Path } else { "$BaseUrl$Path" }
    $args = @{Method=$Method; Uri=$url; Headers=$Headers; TimeoutSec=35; SkipHttpErrorCheck=$true}
    if ($null -ne $Body) { $args.ContentType='application/json'; $args.Body=($Body | ConvertTo-Json -Depth 12 -Compress) }
    $r = Invoke-WebRequest @args
    $data = if ($r.Content) { try { $r.Content | ConvertFrom-Json } catch { $r.Content } } else { $null }
    if ([int]$r.StatusCode -ne $Expected) { throw "$Method $Path expected $Expected, got $($r.StatusCode): $($r.Content)" }
    return $data
}
function Token([string]$Email, [string]$Password) {
    $t = Api POST '/api/identity/login' @{email=$Email;password=$Password}
    return @{Authorization="Bearer $($t.accessToken)"}
}
function Wait-Order([string]$Id, [string]$Status, $Headers) {
    for ($i=0; $i -lt 25; $i++) {
        $o = Api GET "/api/order/$Id" -Headers $Headers
        if ($o.status -eq $Status) { return $o }
        Start-Sleep -Seconds 2
    }
    throw "Order $Id did not reach $Status (last $($o.status))"
}
function Sql([string]$Database, [string]$Query) {
    Push-Location $root
    try {
        $lines = & docker compose exec -T sqlserver sh -c 'exec /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -h -1 -W -d "$1" -Q "$2"' _ $Database "SET NOCOUNT ON; $Query"
        if ($LASTEXITCODE -ne 0) { throw 'SQL verification failed' }
        return ($lines -join "`n").Trim()
    } finally { Pop-Location }
}
$run = [guid]::NewGuid().ToString('N').Substring(0,12)
$password = 'SmokeTest123!'
$email = "customer-$run@test.deliveryapp.local"
$driverEmail = "driver-$run@test.deliveryapp.local"
$customer = Api POST '/api/identity/register' @{email=$email;password=$password;fullName="Integration $run";phone='0912345678'} -Expected 201
$auth = Token $email $password
$adminEmail = if ($config['DEV_ADMIN_EMAIL']) { $config['DEV_ADMIN_EMAIL'] } else {'admin@deliveryapp.local'}
$adminPassword = if ($config['DEV_ADMIN_PASSWORD']) { $config['DEV_ADMIN_PASSWORD'] } else {'Admin123!'}
$admin = Token $adminEmail $adminPassword
$driver = Api POST '/api/identity/admin/users' @{email=$driverEmail;password=$password;fullName="Integration driver $run";phone='0912345679';role='DRIVER'} $admin 201
$driverAuth = Token $driverEmail $password
$orderIds = [System.Collections.Generic.List[string]]::new()
try {
    $ready = Api GET '/health/ready'
    Assert-Check ($ready.status -eq 'Ready' -and @($ready.dependencies).Count -eq 7) 'Gateway checks all seven dependencies'
    $unauthorized = Api GET '/api/identity/me' -Expected 401
    Assert-Check ($unauthorized.code -and $unauthorized.message -and $unauthorized.PSObject.Properties.Name.Contains('details')) '401 has code/message/details'
    $forbidden = Api PATCH '/api/driver/me/availability' @{availabilityStatus='AVAILABLE'} $auth 403
    Assert-Check ($forbidden.code -eq 'FORBIDDEN') '403 has structured body'
    $coords = @{pickupLatitude=10.776889;pickupLongitude=106.700806;dropoffLatitude=10.762622;dropoffLongitude=106.660172}
    $null = Api POST 'http://localhost:6205/api/map/route/quote' $coords $auth 401
    $null = Api POST 'http://localhost:6207/api/promotion/validate' @{code='FREESHIP';customerId=$customer.userId;orderAmount=60000} $auth 401
    $map = Api POST 'http://localhost:6205/api/map/route/quote' $coords $internal
    Assert-Check ($map.distanceKm -gt 0) 'Map/Promotion reject customer JWT; Map accepts internal key'
    $null = Api POST '/api/promotion/validate' @{code='FREESHIP'} $internal 404
    $promos = @(Api GET '/api/promotion')
    Assert-Check ($promos.Count -gt 0 -and $promos[0].code -and $promos[0].endAt) 'Public active promotion list'
    $promo = $promos | Where-Object {$_.code -eq 'FREESHIP'} | Select-Object -First 1
    if (!$promo) { $promo = $promos[0] }
    $request = @{pickupAddress='Integration pickup';dropoffAddress='Integration dropoff';pickupLocation=@{latitude=10.776889;longitude=106.700806};dropoffLocation=@{latitude=10.762622;longitude=106.660172};receiverPhone='0912345678';promotionCode=$promo.code;pointsToUse=2000000000;clientRequestId=[guid]::NewGuid().ToString()}
    $failed = Api POST '/api/order' $request $auth 409
    $status = Sql 'DeliveryApp_Promotion' "SELECT Status FROM PromotionReservations WHERE CustomerId='$($customer.userId)'"
    Assert-Check ($failed.code -eq 'LOYALTY_POINTS_INSUFFICIENT' -and $status -eq 'RELEASED') 'Failed point reservation releases prior promotion immediately'
    $null = Api POST 'http://localhost:6204/api/loyalty/earn' @{orderId=[guid]::NewGuid().ToString();customerId=$customer.userId;amount=1200000} $internal
    $account = Api GET '/api/loyalty/me' -Headers $auth
    Assert-Check ($account.points -eq 120 -and $account.availablePoints -eq 120 -and $account.tier -eq 'SILVER' -and $account.tierDiscountPercent -eq 5) 'Loyalty balance and SILVER metadata'
    $request.pointsToUse=5
    $request.clientRequestId=[guid]::NewGuid().ToString()
    $price = Api POST '/api/order/preview' $request $auth
    $points = Api POST '/api/loyalty/preview' @{points=5;orderAmount=$price.amountAfterPromotion} $auth
    Assert-Check ($points.usablePoints -eq 5 -and $points.discountAmount -eq 5000) 'Customer fee + loyalty preview'
    $account = Api GET '/api/loyalty/me' -Headers $auth
    Assert-Check ($account.availablePoints -eq 120) 'Preview does not reserve points'
    $null = Api PATCH '/api/driver/me/availability' @{availabilityStatus='AVAILABLE'} $driverAuth
    $null = Api PUT '/api/driver/me/location' @{latitude=10.776889;longitude=106.700806;accuracyM=5;recordedAt=[DateTime]::UtcNow.ToString('o')} $driverAuth
    $cancelOrder = Api POST '/api/order' $request $auth 201
    $orderIds.Add($cancelOrder.orderId)
    $account = Api GET '/api/loyalty/me' -Headers $auth
    Assert-Check ($account.availablePoints -eq 115 -and $account.points -eq 120) 'Available balance excludes held points'
    $null = Api POST "/api/order/$($cancelOrder.orderId)/cancel" @{reasonCode='INTEGRATION_TEST';version=$cancelOrder.version} $auth
    for($i=0;$i -lt 20;$i++) {
        $account=Api GET '/api/loyalty/me' -Headers $auth
        if($account.availablePoints -eq 120){break}
        Start-Sleep -Seconds 2
    }
    Assert-Check ($account.availablePoints -eq 120) 'Cancellation releases point hold'
    $request.clientRequestId=[guid]::NewGuid().ToString()
    $order = Api POST '/api/order' $request $auth 201
    $orderIds.Add($order.orderId)
    Assert-Check ($order.totalFee -eq ($price.amountAfterPromotion-$points.discountAmount)) 'Created total matches preview; no tier discount deducted'
    $again=Api POST '/api/order' $request $auth 201
    Assert-Check ($again.orderId -eq $order.orderId) 'Create retry is idempotent'
    $offer=$null
    for($i=0;$i -lt 25;$i++) {
        $offers=Api GET '/api/delivery/offers/me' -Headers $driverAuth
        $offer=$offers.items | Where-Object {$_.orderId -eq $order.orderId -and $_.status -eq 'OFFERED'} | Select-Object -First 1
        if($offer){break}
        Start-Sleep -Seconds 2
    }
    if(!$offer){throw 'No offer for test driver'}
    $accepted=Api POST "/api/delivery/offers/$($offer.offerId)/accept" -Headers $driverAuth
    $null=Wait-Order $order.orderId 'ASSIGNED' $auth
    $delivery=Api GET "/api/delivery/order/$($order.orderId)" -Headers $auth
    Assert-Check ($delivery.pickupLatitude -eq 10.776889 -and $delivery.pickupLongitude -eq 106.700806 -and $delivery.dropoffLatitude -eq 10.762622 -and $delivery.dropoffLongitude -eq 106.660172) 'Tracking response contains exact pickup/dropoff coordinates'
    $location=Api GET "/api/delivery/$($delivery.deliveryId)/location" -Headers $auth
    Assert-Check ($location.latitude -eq 10.776889) 'Tracking driver GPS readable by owning customer'
    $missing=Api POST "/api/delivery/$($delivery.deliveryId)/pickup" @{} $driverAuth 400
    $far=Api POST "/api/delivery/$($delivery.deliveryId)/pickup" @{gpsLatitude=11;gpsLongitude=107} $driverAuth 409
    Assert-Check ($missing.code -eq 'PICKUP_GPS_REQUIRED' -and $far.code -eq 'PICKUP_TOO_FAR') 'Pickup rejects missing/far GPS'
    $null=Api POST "/api/delivery/$($delivery.deliveryId)/pickup" @{gpsLatitude=10.776889;gpsLongitude=106.700806} $driverAuth
    $null=Wait-Order $order.orderId 'PICKED_UP' $auth
    $null=Api POST "/api/delivery/$($delivery.deliveryId)/start" -Headers $driverAuth
    $null=Wait-Order $order.orderId 'DELIVERING' $auth
    $null=Api POST "/api/delivery/$($delivery.deliveryId)/complete" -Headers $driverAuth
    $null=Wait-Order $order.orderId 'COMPLETED' $auth
    for($i=0;$i -lt 20;$i++) {
        $account=Api GET '/api/loyalty/me' -Headers $auth
        if($account.points -eq (115+[math]::Floor($order.totalFee/10000))){break}
        Start-Sleep -Seconds 2
    }
    Assert-Check ($account.points -eq (115+[math]::Floor($order.totalFee/10000))) 'Full delivery commits points and earns reward'
    $null=Api POST "/api/delivery/$($delivery.deliveryId)/complete" -Headers $driverAuth
    $repeated=Api GET '/api/loyalty/me' -Headers $auth
    Assert-Check ($repeated.points -eq $account.points -and @($repeated.recentTransactions | Where-Object {$_.orderId -eq $order.orderId -and $_.type -eq 'EARN'}).Count -eq 1) 'Complete retry does not earn twice'
    [pscustomobject]@{checks=$results.Count;customerId=$customer.userId;driverId=$driver.userId;orders=@($orderIds);result='PASS'} | ConvertTo-Json -Depth 5
} finally {
    foreach($id in $orderIds) {
        try {
            $current=Api GET "/api/order/$id" -Headers $auth
            if($current.status -in @('PENDING','ASSIGNED')) {
                $null=Api POST "/api/order/$id/cancel" @{reasonCode='INTEGRATION_CLEANUP';version=$current.version} $auth
            }
        } catch { Write-Warning "Cleanup needs attention for test order $id" }
    }
    try { $null=Api PATCH '/api/driver/me/availability' @{availabilityStatus='OFFLINE'} $driverAuth } catch { Write-Warning 'Test driver could not be set offline yet' }
}
