# ============================================================================
#  Firewall Windows — autorise les connexions entrantes pour City Hotel LAN
# ============================================================================
#
#  Cible : serveur Windows 192.168.100.141 dans le réseau 192.168.100.0/24.
#  Ouvre les ports 4200 (frontend Angular) et 8080 (backend Spring Boot)
#  uniquement pour le sous-réseau local — pas exposé internet.
#
#  Exécution : Clic-droit > « Exécuter avec PowerShell en administrateur ».
#  Idempotent : `Set-NetFirewallRule` met à jour si la règle existe déjà.
# ============================================================================

#Requires -RunAsAdministrator

$ErrorActionPreference = 'Stop'

$rules = @(
    @{ Name = 'CityHotel-Frontend-4200'; Port = 4200; DisplayName = 'City Hotel - Frontend Angular (LAN)' }
    @{ Name = 'CityHotel-Backend-8080';  Port = 8080; DisplayName = 'City Hotel - Backend Spring Boot (LAN)' }
)

$lanScope = '192.168.100.0/24'

foreach ($rule in $rules) {
    $existing = Get-NetFirewallRule -Name $rule.Name -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Host "[~] Mise à jour de la règle '$($rule.Name)' (port $($rule.Port))..."
        Set-NetFirewallRule -Name $rule.Name `
            -DisplayName $rule.DisplayName `
            -Direction Inbound `
            -Action Allow `
            -Protocol TCP `
            -LocalPort $rule.Port `
            -RemoteAddress $lanScope `
            -Enabled True
    } else {
        Write-Host "[+] Création de la règle '$($rule.Name)' (port $($rule.Port))..."
        New-NetFirewallRule -Name $rule.Name `
            -DisplayName $rule.DisplayName `
            -Direction Inbound `
            -Action Allow `
            -Protocol TCP `
            -LocalPort $rule.Port `
            -RemoteAddress $lanScope `
            -Profile Any `
            -Enabled True | Out-Null
    }
}

Write-Host ""
Write-Host "Règles pare-feu créées/mises à jour :" -ForegroundColor Green
Get-NetFirewallRule -Name 'CityHotel-*' |
    Select-Object Name, DisplayName, Enabled, Direction, Action |
    Format-Table -AutoSize
