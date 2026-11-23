# Kiểm thử luồng khiếu nại qua Gateway trên stack Docker đang chạy. Tạo tài khoản/đơn thử riêng.
# KHÔNG chạy trên production: script sửa CompletedAt/ResolvedAt của đơn thử để giả lập quá hạn.
param(
    [string]$BaseUrl = 'http://localhost:6200',
    [string]$DeliveryUrl = 'http://localhost:6201',
    [string]$EnvFile = '',
    [string[]]$ComposeArgs = @(),
    [int]$CleanupWaitSeconds = 0
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$root = Split-Path $PSScriptRoot -Parent
if (!$EnvFile) { $EnvFile = Join-Path $root '.env' }
$config = @{}
Get-Content $EnvFile | ForEach-Object { if ($_ -match '^([A-Z_]+)=(.*)$') { $config[$matches[1]] = $matches[2].Trim('"') } }
$http = [System.Net.Http.HttpClient]::new()
$http.Timeout = [TimeSpan]::FromSeconds(60)
$script:passed = 0

function Assert-Check([bool]$Condition, [string]$Name) {
    if (!$Condition) { throw "FAIL: $Name" }
    $script:passed++
    Write-Output "PASS: $Name"
}
function Send($Method, [string]$Url, $Content = $null, [string]$Token = '', [hashtable]$Headers = @{}) {
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $Url)
    if ($Token) { $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token) }
    foreach ($key in $Headers.Keys) { [void]$request.Headers.TryAddWithoutValidation($key, [string]$Headers[$key]) }
    if ($null -ne $Content) { $request.Content = $Content }
    $response = $http.SendAsync($request).GetAwaiter().GetResult()
    $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    $text = [Text.Encoding]::UTF8.GetString($bytes)
    $data = $null
    if ($text -and "$($response.Content.Headers.ContentType)" -match 'json') { $data = $text | ConvertFrom-Json }
    [pscustomobject]@{ Status = [int]$response.StatusCode; Data = $data; Text = $text; Bytes = $bytes; Headers = $response.Content.Headers }
}
function Json($Value) {
    [System.Net.Http.StringContent]::new(($Value | ConvertTo-Json -Depth 10 -Compress), [Text.Encoding]::UTF8, 'application/json')
}
function Api([string]$Method, [string]$Path, $Body = $null, [string]$Token = '', [int]$Expected = 200) {
    $content = if ($null -ne $Body) { Json $Body } else { $null }
    $result = Send $Method "$BaseUrl$Path" $content $Token
    if ($result.Status -ne $Expected) { throw "$Method $Path expected $Expected, got $($result.Status): $($result.Text)" }
    $result.Data
}
function Login([string]$Email, [string]$Password) { (Api POST '/api/identity/login' @{ email = $Email; password = $Password }).accessToken }
function ComplaintForm([string]$OrderId, [string]$Category = 'DAMAGED_ITEM', [string]$Description = 'Hàng bị móp khi nhận', [object[]]$Files = @()) {
    $form = [System.Net.Http.MultipartFormDataContent]::new()
    $form.Add([System.Net.Http.StringContent]::new($OrderId), 'orderId')
    $form.Add([System.Net.Http.StringContent]::new($Category), 'category')
    $form.Add([System.Net.Http.StringContent]::new($Description, [Text.Encoding]::UTF8), 'description')
    foreach ($file in $Files) {
        $part = [System.Net.Http.ByteArrayContent]::new([byte[]]$file.Bytes)
        $part.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new($file.Type)
        $form.Add($part, 'files', $file.Name)
    }
    # Dấu phẩy giữ nguyên object: PowerShell sẽ liệt kê MultipartFormDataContent thành mảng nếu trả về trực tiếp.
    return , $form
}
function Post-Complaint([string]$OrderId, [string]$Token, [object[]]$Files = @(), [string]$Category = 'DAMAGED_ITEM') {
    Send POST "$BaseUrl/api/complaint" (ComplaintForm $OrderId $Category 'Hàng bị móp khi nhận' $Files) $Token
}
function Sql([string]$Database, [string]$Query) {
    Push-Location $root
    try {
        # Câu lệnh đi dạng base64, mật khẩu qua biến môi trường: không có dấu nháy và không dính BOM
        # khi PowerShell 5.1 truyền tham số hoặc stdin cho chương trình ngoài.
        $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("SET NOCOUNT ON; $Query`nGO`n"))
        $command = "echo $encoded | base64 -d | SQLCMDPASSWORD=`$MSSQL_SA_PASSWORD /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -h -1 -W -b -d $Database"
        $lines = & docker compose @ComposeArgs exec -T sqlserver sh -c $command
        if ($LASTEXITCODE -ne 0) { throw "SQL failed: $lines" }
        ($lines -join "`n").Trim()
    } finally { Pop-Location }
}
function Wait-Order([string]$Id, [string]$Status, [string]$Token) {
    for ($i = 0; $i -lt 30; $i++) {
        $order = Api GET "/api/order/$Id" -Token $Token
        if ($order.status -eq $Status) { return $order }
        Start-Sleep -Seconds 1
    }
    throw "Order $Id did not reach $Status (last $($order.status))"
}
function New-Delivery([string]$Customer, [string]$Driver, [switch]$StopBeforeComplete) {
    $null = Api PUT '/api/driver/me/location' @{ latitude = 10.776889; longitude = 106.700806; accuracyM = 5; recordedAt = [DateTime]::UtcNow.ToString('o') } $Driver
    $order = Api POST '/api/order' @{
        pickupAddress = 'Complaint pickup'; dropoffAddress = 'Complaint dropoff'; receiverPhone = '0912345678'
        pickupLocation = @{ latitude = 10.776889; longitude = 106.700806 }
        dropoffLocation = @{ latitude = 10.762622; longitude = 106.660172 }
        pointsToUse = 0; clientRequestId = [guid]::NewGuid().ToString()
    } $Customer 201
    $offer = $null
    for ($i = 0; $i -lt 30 -and !$offer; $i++) {
        $offer = (Api GET '/api/delivery/offers/me' -Token $Driver).items | Where-Object { $_.orderId -eq $order.orderId -and $_.status -eq 'OFFERED' } | Select-Object -First 1
        if (!$offer) { Start-Sleep -Seconds 1 }
    }
    if (!$offer) { throw 'No offer for test driver' }
    $accepted = Api POST "/api/delivery/offers/$($offer.offerId)/accept" -Token $Driver
    $null = Wait-Order $order.orderId 'ASSIGNED' $Customer
    $id = $accepted.deliveryId
    $null = Api POST "/api/delivery/$id/pickup" @{ gpsLatitude = 10.776889; gpsLongitude = 106.700806 } $Driver
    $null = Wait-Order $order.orderId 'PICKED_UP' $Customer
    $null = Api POST "/api/delivery/$id/start" -Token $Driver
    $null = Wait-Order $order.orderId 'DELIVERING' $Customer
    if (!$StopBeforeComplete) { Complete-Delivery $order.orderId $id $Customer $Driver }
    [pscustomobject]@{ OrderId = $order.orderId; DeliveryId = $id }
}
function Complete-Delivery([string]$OrderId, [string]$DeliveryId, [string]$Customer, [string]$Driver) {
    $null = Api POST "/api/delivery/$DeliveryId/complete" -Token $Driver
    $null = Wait-Order $OrderId 'COMPLETED' $Customer
    # Tài xế được trả về AVAILABLE qua outbox; chờ để nhận được lời mời của đơn sau.
    for ($i = 0; $i -lt 20; $i++) {
        $result = Send PATCH "$BaseUrl/api/driver/me/availability" (Json @{ availabilityStatus = 'AVAILABLE' }) $Driver
        if ($result.Status -eq 200 -and $result.Data.availabilityStatus -eq 'AVAILABLE') { return }
        Start-Sleep -Seconds 1
    }
    throw 'Driver did not return to AVAILABLE'
}

[byte[]]$png = @(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + (1..64)
[byte[]]$jpeg = @(0xFF, 0xD8, 0xFF, 0xE0) + (1..64)
$run = [guid]::NewGuid().ToString('N').Substring(0, 12)
$password = 'SmokeTest123!'
$adminEmail = if ($config['DEV_ADMIN_EMAIL']) { $config['DEV_ADMIN_EMAIL'] } else { 'admin@deliveryapp.local' }
$adminPassword = if ($config['DEV_ADMIN_PASSWORD']) { $config['DEV_ADMIN_PASSWORD'] } else { 'Admin123!' }
$admin = Login $adminEmail $adminPassword
$customerEmail = "complaint-customer-$run@test.deliveryapp.local"
$otherEmail = "complaint-other-$run@test.deliveryapp.local"
$driverEmail = "complaint-driver-$run@test.deliveryapp.local"
$null = Api POST '/api/identity/register' @{ email = $customerEmail; password = $password; fullName = "Complaint $run"; phone = '0912345678' } -Expected 201
$null = Api POST '/api/identity/register' @{ email = $otherEmail; password = $password; fullName = "Other $run"; phone = '0912345670' } -Expected 201
$null = Api POST '/api/identity/admin/users' @{ email = $driverEmail; password = $password; fullName = "Driver $run"; phone = '0912345679'; role = 'DRIVER' } $admin 201
$customer = Login $customerEmail $password
$other = Login $otherEmail $password
$driver = Login $driverEmail $password
$null = Api PATCH '/api/driver/me/availability' @{ availabilityStatus = 'AVAILABLE' } $driver

try {
    $ready = Api GET '/health/ready'
    Assert-Check ($ready.status -eq 'Ready' -and @($ready.dependencies).Count -eq 8 -and @($ready.dependencies | Where-Object { $_.service -eq 'complaint' -and $_.ready }).Count -eq 1) 'Gateway /health/ready checks all 8 services including complaint'
    Assert-Check ((Api GET '/health/complaint/ready').status -eq 'Ready') 'Complaint health is routed through Gateway'
    Assert-Check ((Send GET "$BaseUrl/api/complaint/me").Status -eq 401) 'Complaint API requires a token'

    $first = New-Delivery $customer $driver -StopBeforeComplete
    $notDone = Post-Complaint $first.OrderId $customer
    Assert-Check ($notDone.Status -eq 409 -and $notDone.Data.code -eq 'COMPLAINT_ORDER_NOT_COMPLETED') 'Order not completed: 409 COMPLAINT_ORDER_NOT_COMPLETED'
    Complete-Delivery $first.OrderId $first.DeliveryId $customer $driver

    $freshDriver = Login $driverEmail $password
    $history = Api GET '/api/delivery/history/me?page=1&pageSize=100' -Token $freshDriver
    $trip = @($history.items | Where-Object { $_.orderId -eq $first.OrderId })
    Assert-Check ($trip.Count -eq 1 -and $trip[0].status -eq 'COMPLETED' -and $trip[0].completedAt -and $null -eq $trip[0].receiverPhone) 'Driver history survives a new login and hides the phone number'
    $null = Api GET '/api/delivery/history/me' -Token $customer -Expected 403
    $null = Api POST '/api/identity/admin/users' @{ email = "complaint-driver2-$run@test.deliveryapp.local"; password = $password; fullName = "Driver2 $run"; phone = '0912345678'; role = 'DRIVER' } $admin 201
    $otherDriver = Login "complaint-driver2-$run@test.deliveryapp.local" $password
    Assert-Check ((Api GET '/api/delivery/history/me' -Token $otherDriver).totalItems -eq 0) 'Driver history is private to each driver'

    $viaGateway = Send GET "$BaseUrl/api/delivery/order/$($first.OrderId)/complaint-context" $null $admin
    Assert-Check ($viaGateway.Status -eq 404) 'Internal complaint-context is not exposed through Gateway'
    $noKey = Send GET "$DeliveryUrl/api/delivery/order/$($first.OrderId)/complaint-context" $null $admin
    Assert-Check ($noKey.Status -eq 401 -and $noKey.Data.code -eq 'INVALID_INTERNAL_KEY') 'Internal complaint-context rejects JWT without internal key'
    $withKey = Send GET "$DeliveryUrl/api/delivery/order/$($first.OrderId)/complaint-context" -Headers @{ 'X-Internal-Key' = $config['INTERNAL_API_KEY'] }
    Assert-Check ($withKey.Status -eq 200 -and $withKey.Text -match '"completedAt":"[^"]+Z"' -and $withKey.Text -notmatch 'receiverPhone') 'Internal context returns UTC time and no phone number'

    $badBytes = Post-Complaint $first.OrderId $customer @(@{ Name = 'fake.png'; Type = 'image/png'; Bytes = [Text.Encoding]::ASCII.GetBytes('<svg onload=alert(1)>') })
    Assert-Check ($badBytes.Status -eq 400 -and $badBytes.Data.code -eq 'COMPLAINT_INVALID_FILE') 'Fake image rejected by magic bytes'
    $gif = Post-Complaint $first.OrderId $customer @(@{ Name = 'a.gif'; Type = 'image/gif'; Bytes = $png })
    Assert-Check ($gif.Status -eq 400 -and $gif.Data.code -eq 'COMPLAINT_INVALID_FILE') 'Disallowed Content-Type rejected'
    $four = 1..4 | ForEach-Object { @{ Name = "p$_.png"; Type = 'image/png'; Bytes = $png } }
    $many = Post-Complaint $first.OrderId $customer $four
    Assert-Check ($many.Status -eq 400 -and $many.Data.code -eq 'COMPLAINT_TOO_MANY_FILES') 'Fourth file rejected'
    $big = [byte[]]::new(5MB + 1); [Array]::Copy($png, $big, 8)
    $tooBig = Post-Complaint $first.OrderId $customer @(@{ Name = 'big.png'; Type = 'image/png'; Bytes = $big })
    Assert-Check ($tooBig.Status -eq 400 -and $tooBig.Data.code -eq 'COMPLAINT_FILE_TOO_LARGE') 'File over 5 MB rejected'
    $huge = [byte[]]::new(17MB); [Array]::Copy($png, $huge, 8)
    $hugeResult = Post-Complaint $first.OrderId $customer @(@{ Name = 'huge.png'; Type = 'image/png'; Bytes = $huge })
    Assert-Check ($hugeResult.Status -in 400, 413 -and $hugeResult.Data.code) "Oversized request returns structured error ($($hugeResult.Status) $($hugeResult.Data.code))"
    $badCategory = Post-Complaint $first.OrderId $customer @() 'NOPE'
    Assert-Check ($badCategory.Status -eq 400 -and $badCategory.Data.code -eq 'COMPLAINT_INVALID_CATEGORY') 'Unknown category rejected'
    $stranger = Post-Complaint $first.OrderId $other
    Assert-Check ($stranger.Status -eq 403 -and $stranger.Data.code -eq 'COMPLAINT_FORBIDDEN') 'Another customer cannot complain about this order'
    $adminPost = Post-Complaint $first.OrderId $admin
    Assert-Check ($adminPost.Status -eq 403) 'Admin cannot file a complaint'

    $created = Post-Complaint $first.OrderId $customer @(@{ Name = '../../evil.png'; Type = 'image/png'; Bytes = $png }, @{ Name = 'b.jpg'; Type = 'image/jpeg'; Bytes = $jpeg })
    Assert-Check ($created.Status -eq 201 -and $created.Data.status -eq 'OPEN' -and @($created.Data.attachments).Count -eq 2) 'Customer complaint within 24h: 201 OPEN with 2 images'
    Assert-Check ($created.Data.attachments[0].fileName -notmatch '[/\\]' -and $created.Text -notmatch 'storedFileName|complaint-uploads') 'Response has no disk path or stored name'
    $complaint = $created.Data
    $second = Post-Complaint $first.OrderId $customer
    Assert-Check ($second.Status -eq 409 -and $second.Data.code -eq 'COMPLAINT_ALREADY_EXISTS') 'Second customer complaint: 409 COMPLAINT_ALREADY_EXISTS'
    $driverComplaint = Post-Complaint $first.OrderId $driver @() 'OTHER'
    Assert-Check ($driverComplaint.Status -eq 201 -and $driverComplaint.Data.reporterRole -eq 'DRIVER') 'Driver can file one complaint for the same order'
    $driverSecond = Post-Complaint $first.OrderId $driver @() 'OTHER'
    Assert-Check ($driverSecond.Status -eq 409 -and $driverSecond.Data.code -eq 'COMPLAINT_ALREADY_EXISTS') 'Second driver complaint: 409'

    $mine = Api GET "/api/complaint/me?orderId=$($first.OrderId)&page=1&pageSize=10" -Token $customer
    Assert-Check ($mine.totalItems -eq 1 -and $mine.items[0].id -eq $complaint.id) 'GET /me returns only own complaint'
    $null = Api GET "/api/complaint/$($complaint.id)" -Token $other -Expected 403
    $null = Api GET "/api/complaint/$($complaint.id)" -Token $driver -Expected 403
    Assert-Check ((Api GET "/api/complaint/$($complaint.id)" -Token $admin).id -eq $complaint.id) 'Detail: owner/admin only'
    $attachment = @($complaint.attachments | Where-Object { $_.contentType -eq 'image/png' })[0]
    $download = Send GET "$BaseUrl/api/complaint/$($complaint.id)/attachments/$($attachment.id)" $null $customer
    Assert-Check ($download.Status -eq 200 -and "$($download.Headers.ContentType)" -eq 'image/png' -and [Convert]::ToBase64String($download.Bytes) -eq [Convert]::ToBase64String($png)) 'Owner downloads the exact image bytes'
    $adminDownload = Send GET "$BaseUrl/api/complaint/$($complaint.id)/attachments/$($attachment.id)" $null $admin
    Assert-Check ($adminDownload.Status -eq 200) 'Admin can download the image'
    Assert-Check ((Send GET "$BaseUrl/api/complaint/$($complaint.id)/attachments/$($attachment.id)" $null $other).Status -eq 403) 'Other user cannot download the image'
    Assert-Check ((Send GET "$BaseUrl/api/complaint/$($complaint.id)/attachments/$($attachment.id)").Status -eq 401) 'Anonymous cannot download the image'

    $null = Api GET '/api/complaint/admin' -Token $customer -Expected 403
    $open = Api GET '/api/complaint/admin?status=OPEN&page=1&pageSize=100' -Token $admin
    Assert-Check (@($open.items | Where-Object { $_.id -eq $complaint.id }).Count -eq 1) 'Admin list filters by status'
    $path = "/api/complaint/admin/$($complaint.id)/status"
    $null = Api PATCH $path @{ status = 'IN_REVIEW' } $customer 403
    $skip = Api PATCH $path @{ status = 'RESOLVED' } $admin 409
    Assert-Check ($skip.code -eq 'COMPLAINT_INVALID_TRANSITION') 'OPEN to RESOLVED: 409 COMPLAINT_INVALID_TRANSITION'
    $review = Api PATCH $path @{ status = 'IN_REVIEW'; adminNote = 'Đang kiểm tra' } $admin
    Assert-Check ($review.status -eq 'IN_REVIEW' -and $review.handledByAdminId) 'Admin moves complaint to IN_REVIEW'
    $noNote = Api PATCH $path @{ status = 'REJECTED' } $admin 400
    Assert-Check ($noNote.code -eq 'COMPLAINT_ADMIN_NOTE_REQUIRED') 'REJECTED requires admin note'
    $resolved = Api PATCH $path @{ status = 'RESOLVED'; adminNote = 'Đã liên hệ khách' } $admin
    Assert-Check ($resolved.status -eq 'RESOLVED' -and $resolved.resolvedAt -and $resolved.adminNote -eq 'Đã liên hệ khách') 'Admin resolves complaint'
    $back = Api PATCH $path @{ status = 'IN_REVIEW' } $admin 409
    Assert-Check ($back.code -eq 'COMPLAINT_INVALID_TRANSITION') 'Final status cannot go back'
    $seen = Api GET "/api/complaint/$($complaint.id)" -Token $customer
    Assert-Check ($seen.adminNote -eq 'Đã liên hệ khách' -and $null -eq $seen.handledByAdminId) 'Customer sees admin note but not admin id'

    $late = New-Delivery $customer $driver
    $null = Sql 'DeliveryApp_Delivery' "UPDATE Deliveries SET CompletedAt = DATEADD(MINUTE, -1441, SYSUTCDATETIME()) WHERE OrderId = '$($late.OrderId)'"
    $closed = Post-Complaint $late.OrderId $customer
    Assert-Check ($closed.Status -eq 409 -and $closed.Data.code -eq 'COMPLAINT_WINDOW_CLOSED') 'Past 24h: 409 COMPLAINT_WINDOW_CLOSED'
    $null = Sql 'DeliveryApp_Delivery' "UPDATE Deliveries SET CompletedAt = DATEADD(MINUTE, -1439, SYSUTCDATETIME()) WHERE OrderId = '$($late.OrderId)'"
    $inside = Post-Complaint $late.OrderId $customer
    Assert-Check ($inside.Status -eq 201) 'Just inside 24h: 201'

    if ($CleanupWaitSeconds -gt 0) {
        $null = Sql 'DeliveryApp_Complaint' "UPDATE Complaints SET ResolvedAt = DATEADD(DAY, -31, SYSUTCDATETIME()) WHERE Id = '$($complaint.id)'"
        $left = '2'
        for ($i = 0; $i -lt $CleanupWaitSeconds -and $left -ne '0'; $i += 3) {
            Start-Sleep -Seconds 3
            $left = Sql 'DeliveryApp_Complaint' "SELECT COUNT(*) FROM ComplaintAttachments WHERE ComplaintId = '$($complaint.id)'"
        }
        $gone = Send GET "$BaseUrl/api/complaint/$($complaint.id)/attachments/$($attachment.id)" $null $admin
        Assert-Check ($left -eq '0' -and $gone.Status -eq 404) 'Cleanup removes images of a final complaint older than 30 days'
    }
    [pscustomobject]@{ checks = $script:passed; result = 'PASS' } | ConvertTo-Json
} finally {
    try { $null = Send PATCH "$BaseUrl/api/driver/me/availability" (Json @{ availabilityStatus = 'OFFLINE' }) $driver } catch { Write-Warning 'Test driver could not be set offline' }
    $http.Dispose()
}
