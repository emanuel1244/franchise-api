# Carga datos de ejemplo realistas usando la API (respeta validaciones y el diseno de claves).
# Uso: .\scripts\seed-data.ps1 [-BaseUrl http://localhost:8080]
param([string]$BaseUrl = "http://localhost:8080")

$ErrorActionPreference = "Stop"
$api = "$BaseUrl/api/franchises"

function Post($url, $body) {
    Invoke-RestMethod -Method Post -Uri $url -TimeoutSec 10 -ContentType "application/json; charset=utf-8" `
        -Body ([Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Compress)))
}

$data = [ordered]@{
    "Cafe Aroma Andino" = [ordered]@{
        "Sede Chapinero - Bogota"   = @(@("Cafe Americano", 120), @("Capuchino", 85), @("Latte Vainilla", 64), @("Croissant de Almendras", 40), @("Torta de Zanahoria", 18))
        "Sede El Poblado - Medellin" = @(@("Cafe Americano", 95), @("Espresso Doble", 140), @("Mocaccino", 52), @("Pandebono", 75))
        "Sede Granada - Cali"        = @(@("Cafe Americano", 60), @("Cold Brew", 88), @("Chai Latte", 33), @("Galleta de Avena", 47))
    }
    "Pizzeria Bella Napoli" = [ordered]@{
        "Sede Usaquen - Bogota"      = @(@("Pizza Margarita", 35), @("Pizza Pepperoni", 42), @("Pizza Cuatro Quesos", 28), @("Lasana Bolonesa", 15), @("Tiramisu", 22))
        "Sede Laureles - Medellin"   = @(@("Pizza Margarita", 30), @("Pizza Hawaiana", 48), @("Calzone", 19), @("Pan de Ajo", 60))
        "Sede Bocagrande - Cartagena" = @(@("Pizza Marinera", 26), @("Pizza Margarita", 24), @("Ensalada Caprese", 17))
    }
    "Farmacia Vida Sana" = [ordered]@{
        "Sede Centro - Bucaramanga"  = @(@("Acetaminofen 500mg x20", 320), @("Ibuprofeno 400mg x10", 210), @("Vitamina C 1g x30", 145), @("Alcohol Antiseptico 350ml", 90), @("Tapabocas x50", 400))
        "Sede Cabecera - Bucaramanga" = @(@("Acetaminofen 500mg x20", 280), @("Loratadina 10mg x10", 130), @("Suero Oral 500ml", 75), @("Gel Antibacterial 250ml", 160))
        "Sede Norte - Barranquilla"  = @(@("Protector Solar FPS50", 110), @("Acetaminofen 500mg x20", 190), @("Crema Hidratante 200ml", 65))
        "Sede Nueva (sin inventario)" = @()
    }
    "Ferreteria El Constructor" = [ordered]@{
        "Sede Kennedy - Bogota"      = @(@("Cemento Gris 50kg", 250), @("Varilla Corrugada 3/8", 600), @("Pintura Blanca Galon", 85), @("Tornillo Drywall x100", 140))
        "Sede Itagui - Medellin"     = @(@("Cemento Gris 50kg", 180), @("Teja Fibrocemento", 95), @("Tubo PVC 1/2 pulgada", 320), @("Llave Inglesa 10", 25))
    }
}

$summary = @()
foreach ($fname in $data.Keys) {
    $f = Post $api @{ name = $fname }
    foreach ($bname in $data[$fname].Keys) {
        $b = Post "$api/$($f.id)/branches" @{ name = $bname }
        foreach ($p in $data[$fname][$bname]) {
            Post "$api/$($f.id)/branches/$($b.id)/products" @{ name = $p[0]; stock = $p[1] } | Out-Null
        }
    }
    $summary += [pscustomobject]@{ Franquicia = $fname; Sucursales = $data[$fname].Count; Id = $f.id }
}

$summary | Format-Table -AutoSize
