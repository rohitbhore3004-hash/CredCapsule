#Requires -Version 5.1
<#
.SYNOPSIS
    CredCapsule PowerShell module — wraps the Python CLI via subprocess.
.DESCRIPTION
    All cmdlets call credcapsule_single.py and parse the returned JSON.
    Passwords are accepted as plain strings or SecureString; SecureString
    is decrypted in-process and never written to disk.
#>

Set-StrictMode -Version Latest

$script:PythonExe  = $null
$script:ScriptPath = $null
$script:VaultPath  = $null
$script:VaultPass  = $null   # plain string, held only while vault is "open"

# ── module init ────────────────────────────────────────────────────────────────

function _ResolvePython {
    if ($script:PythonExe) { return }
    foreach ($candidate in @("python3", "python", "py")) {
        if (Get-Command $candidate -ErrorAction SilentlyContinue) {
            $script:PythonExe = $candidate
            return
        }
    }
    throw "Python 3 not found. Install Python 3.9+ and ensure it is on PATH."
}

function _ResolveScript {
    if ($script:ScriptPath) { return }
    $candidates = @(
        (Join-Path $PSScriptRoot "credcapsule_single.py"),
        (Join-Path (Split-Path $PSScriptRoot -Parent) "credcapsule_single.py")
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { $script:ScriptPath = $c; return }
    }
    throw "credcapsule_single.py not found next to CredCapsule.psm1"
}

function _Invoke {
    param([string[]]$Args)
    _ResolvePython
    _ResolveScript
    $out = & $script:PythonExe $script:ScriptPath @Args 2>&1
    $stdout = ($out | Where-Object { $_ -isnot [System.Management.Automation.ErrorRecord] }) -join "`n"
    $stderr = ($out | Where-Object { $_ -is  [System.Management.Automation.ErrorRecord] }) -join "`n"
    if ($LASTEXITCODE -ne 0) {
        $errObj = $stderr | ConvertFrom-Json -ErrorAction SilentlyContinue
        $msg    = if ($errObj -and $errObj.error) { $errObj.error } else { $stderr.Trim() }
        throw $msg
    }
    return $stdout | ConvertFrom-Json
}

function _DecryptSecure([System.Security.SecureString]$ss) {
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($ss)
    try   { return [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr) }
    finally { [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

function _GetPass([System.Security.SecureString]$SecurePassword, [string]$PlainPassword) {
    if ($SecurePassword) { return _DecryptSecure $SecurePassword }
    if ($PlainPassword)  { return $PlainPassword }
    if ($script:VaultPass) { return $script:VaultPass }
    $ss = Read-Host "Vault password" -AsSecureString
    return _DecryptSecure $ss
}


# ── Open-CredVault ─────────────────────────────────────────────────────────────

function Open-CredVault {
    <#
    .SYNOPSIS  Open (and optionally create) a CredCapsule vault.
    .PARAMETER Path          Path to the .ccv file.
    .PARAMETER Password      Plain-text password.
    .PARAMETER SecurePassword  SecureString password.
    .PARAMETER Create        Create the vault if it does not exist.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory, Position = 0)] [string]$Path,
        [string]$Password,
        [System.Security.SecureString]$SecurePassword,
        [switch]$Create
    )
    $pw = _GetPass $SecurePassword $Password
    if ($Create -and -not (Test-Path $Path)) {
        _Invoke @("--vault", $Path, "--password", $pw, "create") | Out-Null
    }
    $script:VaultPath = $Path
    $script:VaultPass = $pw
    Write-Verbose "Vault opened: $Path"
}


# ── Close-CredVault ────────────────────────────────────────────────────────────

function Close-CredVault {
    <#.SYNOPSIS  Clear cached vault path and password from memory.#>
    [CmdletBinding()]
    param()
    $script:VaultPath = $null
    if ($script:VaultPass) {
        # Overwrite the string in memory (best-effort; .NET strings are immutable)
        $script:VaultPass = $null
    }
    Write-Verbose "Vault closed"
}


# ── Get-CredVaultInfo ──────────────────────────────────────────────────────────

function Get-CredVaultInfo {
    <#.SYNOPSIS  Return vault metadata without unlocking.#>
    [CmdletBinding()]
    param([string]$VaultPath = $script:VaultPath)
    if (-not $VaultPath) { throw "No vault is open. Run Open-CredVault first." }
    _Invoke @("--vault", $VaultPath, "--no-prompt", "info")
}


# ── Set-CredVaultLogin ─────────────────────────────────────────────────────────

function Set-CredVaultLogin {
    <#.SYNOPSIS  Store or update a login credential.#>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$Label,
        [Parameter(Mandatory)] [string]$Username,
        [Parameter(Mandatory)] [string]$Password,
        [string]$Url,
        [string]$VaultPath = $script:VaultPath,
        [string]$VaultPassword,
        [System.Security.SecureString]$SecureVaultPassword
    )
    if (-not $VaultPath) { throw "No vault is open." }
    $pw   = _GetPass $SecureVaultPassword $VaultPassword
    $args = @("--vault", $VaultPath, "--password", $pw, "set",
              "--label", $Label, "--username", $Username, "--value", $Password,
              "--kind", "login")
    if ($Url) { $args += @("--url", $Url) }
    _Invoke $args
}


# ── Set-CredVaultApiKey ────────────────────────────────────────────────────────

function Set-CredVaultApiKey {
    <#.SYNOPSIS  Store or update an API key credential.#>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$Label,
        [Parameter(Mandatory)] [string]$KeyId,
        [Parameter(Mandatory)] [string]$Secret,
        [string]$Service,
        [string]$VaultPath = $script:VaultPath,
        [string]$VaultPassword,
        [System.Security.SecureString]$SecureVaultPassword
    )
    if (-not $VaultPath) { throw "No vault is open." }
    $pw   = _GetPass $SecureVaultPassword $VaultPassword
    $args = @("--vault", $VaultPath, "--password", $pw, "set",
              "--label", $Label, "--key-id", $KeyId, "--value", $Secret,
              "--kind", "api_key")
    if ($Service) { $args += @("--service", $Service) }
    _Invoke $args
}


# ── Set-CredVaultNote ──────────────────────────────────────────────────────────

function Set-CredVaultNote {
    <#.SYNOPSIS  Store or update a secure note.#>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$Label,
        [Parameter(Mandatory)] [string]$Content,
        [string]$VaultPath = $script:VaultPath,
        [string]$VaultPassword,
        [System.Security.SecureString]$SecureVaultPassword
    )
    if (-not $VaultPath) { throw "No vault is open." }
    $pw = _GetPass $SecureVaultPassword $VaultPassword
    _Invoke @("--vault", $VaultPath, "--password", $pw, "set",
              "--label", $Label, "--value", $Content, "--kind", "note")
}


# ── Get-CredVaultLogin ─────────────────────────────────────────────────────────

function Get-CredVaultLogin {
    <#.SYNOPSIS  Retrieve a login credential. Use -AsSecureString for the password field.#>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$Label,
        [switch]$AsSecureString,
        [string]$VaultPath = $script:VaultPath,
        [string]$VaultPassword,
        [System.Security.SecureString]$SecureVaultPassword
    )
    if (-not $VaultPath) { throw "No vault is open." }
    $pw  = _GetPass $SecureVaultPassword $VaultPassword
    $obj = _Invoke @("--vault", $VaultPath, "--password", $pw, "get-login", "--label", $Label)
    if ($AsSecureString -and $obj.password) {
        $ss = ConvertTo-SecureString $obj.password -AsPlainText -Force
        $obj | Add-Member -NotePropertyName "securePassword" -NotePropertyValue $ss -Force
        $obj.password = $null
    }
    $obj
}


# ── Get-CredVaultApiKey ────────────────────────────────────────────────────────

function Get-CredVaultApiKey {
    <#.SYNOPSIS  Retrieve an API key credential.#>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$Label,
        [string]$VaultPath = $script:VaultPath,
        [string]$VaultPassword,
        [System.Security.SecureString]$SecureVaultPassword
    )
    if (-not $VaultPath) { throw "No vault is open." }
    $pw = _GetPass $SecureVaultPassword $VaultPassword
    _Invoke @("--vault", $VaultPath, "--password", $pw, "get-login", "--label", $Label)
}


# ── Get-CredVaultSecret ────────────────────────────────────────────────────────

function Get-CredVaultSecret {
    <#.SYNOPSIS  Return only the primary secret string for any credential type.#>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$Label,
        [string]$VaultPath = $script:VaultPath,
        [string]$VaultPassword,
        [System.Security.SecureString]$SecureVaultPassword
    )
    if (-not $VaultPath) { throw "No vault is open." }
    $pw  = _GetPass $SecureVaultPassword $VaultPassword
    $obj = _Invoke @("--vault", $VaultPath, "--password", $pw, "get", "--label", $Label)
    $obj.secret
}


# ── Test-CredVaultEntry ────────────────────────────────────────────────────────

function Test-CredVaultEntry {
    <#.SYNOPSIS  Return $true if the label exists, $false otherwise.#>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$Label,
        [string]$VaultPath = $script:VaultPath,
        [string]$VaultPassword,
        [System.Security.SecureString]$SecureVaultPassword
    )
    if (-not $VaultPath) { throw "No vault is open." }
    $pw   = _GetPass $SecureVaultPassword $VaultPassword
    $list = (_Invoke @("--vault", $VaultPath, "--password", $pw, "list")).labels
    return $list -contains $Label
}


# ── Get-CredVaultEntries ───────────────────────────────────────────────────────

function Get-CredVaultEntries {
    <#.SYNOPSIS  List all credential labels in the vault.#>
    [CmdletBinding()]
    param(
        [string]$VaultPath = $script:VaultPath,
        [string]$VaultPassword,
        [System.Security.SecureString]$SecureVaultPassword
    )
    if (-not $VaultPath) { throw "No vault is open." }
    $pw  = _GetPass $SecureVaultPassword $VaultPassword
    $obj = _Invoke @("--vault", $VaultPath, "--password", $pw, "list")
    [PSCustomObject]@{ Labels = $obj.labels; Count = $obj.count }
}


# ── Remove-CredVaultEntry ──────────────────────────────────────────────────────

function Remove-CredVaultEntry {
    <#.SYNOPSIS  Delete a credential from the vault.#>
    [CmdletBinding(SupportsShouldProcess)]
    param(
        [Parameter(Mandatory)] [string]$Label,
        [string]$VaultPath = $script:VaultPath,
        [string]$VaultPassword,
        [System.Security.SecureString]$SecureVaultPassword
    )
    if (-not $VaultPath) { throw "No vault is open." }
    if ($PSCmdlet.ShouldProcess($Label, "Delete credential")) {
        $pw = _GetPass $SecureVaultPassword $VaultPassword
        _Invoke @("--vault", $VaultPath, "--password", $pw, "delete", "--label", $Label)
    }
}


# ── Set-CredVaultPassword ──────────────────────────────────────────────────────

function Set-CredVaultPassword {
    <#.SYNOPSIS  Re-encrypt the vault with a new master password.#>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$NewPassword,
        [string]$VaultPath = $script:VaultPath,
        [string]$VaultPassword,
        [System.Security.SecureString]$SecureVaultPassword
    )
    if (-not $VaultPath) { throw "No vault is open." }
    $pw = _GetPass $SecureVaultPassword $VaultPassword
    _Invoke @("--vault", $VaultPath, "--password", $pw, "rotate", "--new-password", $NewPassword)
    $script:VaultPass = $NewPassword
}


# ── Remove-CredVault ───────────────────────────────────────────────────────────

function Remove-CredVault {
    <#.SYNOPSIS  Permanently delete the vault file.#>
    [CmdletBinding(SupportsShouldProcess)]
    param(
        [string]$VaultPath = $script:VaultPath,
        [string]$VaultPassword,
        [System.Security.SecureString]$SecureVaultPassword
    )
    if (-not $VaultPath) { throw "No vault is open." }
    if ($PSCmdlet.ShouldProcess($VaultPath, "Destroy vault")) {
        $pw = _GetPass $SecureVaultPassword $VaultPassword
        _Invoke @("--vault", $VaultPath, "--password", $pw, "destroy")
        Close-CredVault
    }
}


# ── exports ────────────────────────────────────────────────────────────────────

Export-ModuleMember -Function @(
    "Open-CredVault",
    "Close-CredVault",
    "Get-CredVaultInfo",
    "Set-CredVaultLogin",
    "Set-CredVaultApiKey",
    "Set-CredVaultNote",
    "Get-CredVaultLogin",
    "Get-CredVaultApiKey",
    "Get-CredVaultSecret",
    "Test-CredVaultEntry",
    "Get-CredVaultEntries",
    "Remove-CredVaultEntry",
    "Set-CredVaultPassword",
    "Remove-CredVault"
)
