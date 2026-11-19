# Prueba de humo end-to-end de todos los endpoints.
# Uso: .\scripts\smoke-test.ps1 [-BaseUrl http://localhost:8080]
param([string]$BaseUrl = "http://localhost:8080")

$ErrorActionPreference = "Stop"
$api = "$BaseUrl/api/franchises"
$results = @()

function Call($method, $url, $body = $null) {
    $params = @{ Method = $method; Uri = $url; TimeoutSec = 10; ContentType = "application/json" }
    if ($body) { $params.Body = ($body | ConvertTo-Json -Compress) }
    try {
        $r = Invoke-WebRequest @params -UseBasicParsing
        return @{ Status = [int]$r.StatusCode; Body = if ($r.Content) { $r.Content | ConvertFrom-Json } else { $null } }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) { return @{ Status = [int]$resp.StatusCode; Body = $null } }
        throw
    }
}

function Check($name, $expected, $res) {
    $ok = $res.Status -eq $expected
    $script:results += [pscustomobject]@{ Prueba = $name; Esperado = $expected; Obtenido = $res.Status; OK = $ok }
}

$f = Call POST $api @{ name = "Franquicia Demo" };                          Check "Crear franquicia" 201 $f
$fid = $f.Body.id
$b1 = Call POST "$api/$fid/branches" @{ name = "Sucursal Norte" };          Check "Crear sucursal 1" 201 $b1
$b2 = Call POST "$api/$fid/branches" @{ name = "Sucursal Sur" };            Check "Crear sucursal 2" 201 $b2
$b1id = $b1.Body.id; $b2id = $b2.Body.id
$p1 = Call POST "$api/$fid/branches/$b1id/products" @{ name = "Cafe"; stock = 10 };  Check "Crear producto 1" 201 $p1
$p2 = Call POST "$api/$fid/branches/$b1id/products" @{ name = "Te"; stock = 50 };    Check "Crear producto 2" 201 $p2
$p3 = Call POST "$api/$fid/branches/$b2id/products" @{ name = "Pan"; stock = 5 };    Check "Crear producto 3" 201 $p3
$p1id = $p1.Body.id; $p2id = $p2.Body.id

$s = Call PATCH "$api/$fid/branches/$b1id/products/$p1id/stock" @{ stock = 99 };     Check "Modificar stock" 200 $s
$top = Call GET "$api/$fid/top-stock-products";                                      Check "Top stock por sucursal" 200 $top
$d = Call DELETE "$api/$fid/branches/$b1id/products/$p2id";                          Check "Eliminar producto" 204 $d
$rf = Call PUT "$api/$fid" @{ name = "Franquicia Renombrada" };                      Check "Renombrar franquicia" 200 $rf
$rb = Call PUT "$api/$fid/branches/$b1id" @{ name = "Norte Centro" };                Check "Renombrar sucursal" 200 $rb
$rp = Call PUT "$api/$fid/branches/$b1id/products/$p1id" @{ name = "Cafe Premium" }; Check "Renombrar producto" 200 $rp
$g = Call GET "$api/$fid";                                                           Check "Consultar franquicia" 200 $g

# Casos de error
Check "404 franquicia inexistente" 404 (Call GET "$api/no-existe")
Check "404 sucursal inexistente" 404 (Call POST "$api/$fid/branches/no-existe/products" @{ name = "X"; stock = 1 })
Check "400 stock negativo" 400 (Call PATCH "$api/$fid/branches/$b1id/products/$p1id/stock" @{ stock = -1 })
Check "400 nombre vacio" 400 (Call POST $api @{ name = "" })
Check "404 eliminar producto ya eliminado" 404 (Call DELETE "$api/$fid/branches/$b1id/products/$p2id")

$results | Format-Table -AutoSize
"Top stock por sucursal:"; $top.Body | Format-Table -AutoSize
"Franquicia final:"; $g.Body | ConvertTo-Json -Depth 5
"franchiseId=$fid"
if ($results | Where-Object { -not $_.OK }) { exit 1 }
