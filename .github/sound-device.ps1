#
# Paula Escobar is a terminal music player for demoscene and chip music.
# Copyright © 2026 Adam Waldenberg, Adeptum AB, Org.nr 559494-1824.
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.
#
# This program is distributed in the hope that it will be useful, but
# WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
# or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
# more details.
#
# You should have received a copy of the GNU General Public License along
# with this program. If not, see <https://www.gnu.org/licenses/>.
#
# Website: https://www.adeptum.se
# Contact: info@adeptum.se
#
# Gives a Windows runner a sound device, which it has none of. Scream is
# a virtual sound card whose driver signature has run out, so the catalog
# is signed again with a certificate made here and trusted here; that
# passes because the runners boot with test signing on. The audio service
# is not running on the runners either until it is started.

$ErrorActionPreference = 'Stop'

$release = 'https://github.com/duncanthrax/scream/releases/download/4.0/Scream4.0.zip'
$sha256 = 'fa33e25f9a46c61e4e0cd83362c51c3d2a45c6fe4091aad7507e240e40f1a520'
$work = Join-Path $env:RUNNER_TEMP 'scream'
$zip = "$work.zip"
$driver = Join-Path $work 'Install\driver\x64'
$devcon = Join-Path $work 'Install\helpers\devcon-x64.exe'
$certificate = Join-Path $env:RUNNER_TEMP 'scream-signing'

function Invoke-Checked {
    & $args[0] $args[1..($args.Length - 1)]
    if ($LASTEXITCODE -ne 0) {
        throw "$($args[0]) failed with exit code $LASTEXITCODE"
    }
}

Invoke-WebRequest $release -OutFile $zip
$actual = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $sha256) {
    throw "Scream4.0.zip has checksum $actual, not $sha256"
}
Expand-Archive -Path $zip -DestinationPath $work

Invoke-Checked openssl req -batch -x509 -newkey rsa -nodes -extensions v3_req -subj '/CN=Paula Escobar build' `
    -keyout "$certificate.key" -out "$certificate.cer"
Invoke-Checked openssl pkcs12 -export -nodes -in "$certificate.cer" -inkey "$certificate.key" -out "$certificate.pfx" -passout pass:
$signtool = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin\*\x64\signtool.exe" | Sort-Object FullName | Select-Object -Last 1
Invoke-Checked $signtool.FullName sign /fd SHA256 /f "$certificate.pfx" (Join-Path $driver 'scream.cat')
Import-Certificate -FilePath "$certificate.cer" -CertStoreLocation Cert:\LocalMachine\Root | Out-Null
Import-Certificate -FilePath "$certificate.cer" -CertStoreLocation Cert:\LocalMachine\TrustedPublisher | Out-Null

Invoke-Checked $devcon install (Join-Path $driver 'Scream.inf') '*Scream'
Start-Service audiosrv
Get-CimInstance Win32_SoundDevice | Format-Table Name, Status
