param(
    [string]$MailUsername,
    [string]$MailAuthCode,
    [string]$MailFrom,
    [string]$SpugSmsUrl
)

$ErrorActionPreference = 'Stop'

if (-not $MailUsername) { $MailUsername = Read-Host '请输入邮箱账号' }
if (-not $MailAuthCode) { $MailAuthCode = Read-Host '请输入 SMTP 授权码' }
if (-not $MailFrom) { $MailFrom = $MailUsername }
if (-not $SpugSmsUrl) { $SpugSmsUrl = Read-Host '请输入 Spug 短信模板完整地址' }

$target = Join-Path $PSScriptRoot '..\src\main\resources\application-local.properties'
$target = [IO.Path]::GetFullPath($target)

$content = @"
# 本机真实服务配置（已被 Git 忽略）
app.email.mock=false
app.email.from=$MailFrom
spring.mail.host=smtp.qq.com
spring.mail.port=465
spring.mail.username=$MailUsername
spring.mail.password=$MailAuthCode
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=false
spring.mail.properties.mail.smtp.ssl.enable=true
spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=5000
spring.mail.properties.mail.smtp.writetimeout=5000
spring.mail.default-encoding=UTF-8

app.sms.mock=false
app.sms.spug.url=$SpugSmsUrl
app.sms.spug.number=5
"@

[IO.File]::WriteAllText($target, $content, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "本地配置已生成：$target"
Write-Host '该文件不会提交到 Git。请勿把真实授权码发到公开聊天或仓库。'