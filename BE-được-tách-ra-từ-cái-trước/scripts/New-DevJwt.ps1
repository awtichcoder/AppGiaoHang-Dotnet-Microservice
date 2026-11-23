param(
    [ValidateSet('CUSTOMER', 'DRIVER', 'ADMIN')]
    [string]$Role = 'CUSTOMER',
    [Guid]$SubjectId = [Guid]::NewGuid(),
    [string]$Email = 'dev@deliveryapp.local',
    [int]$ExpiresInMinutes = 60
)

$secret = $env:JWT_SECRET
if ([string]::IsNullOrWhiteSpace($secret) -and (Test-Path -LiteralPath '.env')) {
    $line = Get-Content -LiteralPath '.env' | Where-Object { $_ -match '^JWT_SECRET=' } | Select-Object -First 1
    if ($line) { $secret = $line.Substring('JWT_SECRET='.Length) }
}

if ([string]::IsNullOrWhiteSpace($secret) -or $secret.Length -lt 32) {
    throw 'JWT_SECRET phải có ít nhất 32 ký tự. Hãy tạo .env từ .env.example trước.'
}

function ConvertTo-Base64Url([byte[]]$Bytes) {
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$now = [DateTimeOffset]::UtcNow
$header = @{ alg = 'HS256'; typ = 'JWT' } | ConvertTo-Json -Compress
$payload = @{
    sub = $SubjectId.ToString()
    email = $Email
    role = $Role
    iss = 'deliveryapp-identity'
    iat = $now.ToUnixTimeSeconds()
    exp = $now.AddMinutes($ExpiresInMinutes).ToUnixTimeSeconds()
} | ConvertTo-Json -Compress

$headerPart = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($header))
$payloadPart = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payload))
$unsigned = "$headerPart.$payloadPart"
$hmac = [Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($secret))
try {
    $signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsigned)))
    "$unsigned.$signature"
}
finally {
    $hmac.Dispose()
}
