<#
  Monta o instalador do VYBE para Windows (VYBE-Setup-x.y.z.exe em desktop\dist, mais o latest.yml da atualização automática).

  Uso, na pasta vybe:
    powershell -ExecutionPolicy Bypass -File desktop\build.ps1               # gera o instalador
    powershell -ExecutionPolicy Bypass -File desktop\build.ps1 -SoPreparar   # só prepara; depois "npm start" em desktop

  Precisa de: JDK 21 (com jlink) no JAVA_HOME, Maven e Node.js.
#>
param(
    [switch]$SoPreparar
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$saida = Join-Path $PSScriptRoot 'build'

function Passo($texto) { Write-Host "`n==> $texto" -ForegroundColor Cyan }

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\jlink.exe'))) {
    throw 'JAVA_HOME precisa apontar para um JDK 21 (com jlink). Ex.: C:\Program Files\Eclipse Adoptium\jdk-21...'
}

Passo 'Limpando a pasta de montagem'
if (Test-Path $saida) { Remove-Item $saida -Recurse -Force }
New-Item -ItemType Directory $saida | Out-Null

Passo '1/5 API (Spring Boot com o banco embutido)'
Push-Location (Join-Path $raiz 'backend')
try {
    mvn -q -Pdesktop -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar a API' }
} finally { Pop-Location }
$jar = Get-ChildItem (Join-Path $raiz 'backend\target') -Filter 'vybe-api-*.jar' |
    Where-Object { $_.Name -notlike '*.original' } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 # o mais novo, se houver de outra versão
Copy-Item $jar.FullName (Join-Path $saida 'vybe-api.jar')

Passo '2/5 Telas (React), falando com a API no mesmo endereço'
Push-Location (Join-Path $raiz 'frontend')
try {
    # "npm install" (e não "npm ci") para não apagar a pasta node_modules, que o "npm run dev"
    # pode estar usando. Se o front-end estiver rodando, pare-o antes (Ctrl+C).
    npm install
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar as dependências do front-end. Se o "npm run dev" estiver aberto, pare-o com Ctrl+C e rode de novo.' }
    $env:VITE_API_URL = '/api'
    $env:VITE_PROGRAMA = '1'
    npm run build
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao montar o front-end' }
} finally {
    Remove-Item Env:VITE_API_URL -ErrorAction SilentlyContinue
    Remove-Item Env:VITE_PROGRAMA -ErrorAction SilentlyContinue
    Pop-Location
}
Copy-Item (Join-Path $raiz 'frontend\dist') (Join-Path $saida 'frontend') -Recurse

Passo '3/5 Java enxuto para o instalador (jlink)'
$modulos = @(
    'java.base', 'java.compiler', 'java.desktop', 'java.instrument', 'java.logging', 'java.management',
    'java.naming', 'java.net.http', 'java.prefs', 'java.rmi', 'java.scripting', 'java.security.jgss',
    'java.security.sasl', 'java.sql', 'java.transaction.xa', 'java.xml', 'jdk.crypto.ec', 'jdk.localedata',
    'jdk.management', 'jdk.naming.dns', 'jdk.net', 'jdk.unsupported', 'jdk.zipfs'
) -join ','
& (Join-Path $env:JAVA_HOME 'bin\jlink.exe') --add-modules $modulos --output (Join-Path $saida 'jre') `
    --strip-debug --no-header-files --no-man-pages --compress=zip-6 --generate-cds-archive
if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar o Java enxuto' }

Passo '4/5 Ícone'
Copy-Item (Join-Path $raiz 'frontend\public\logo.png') (Join-Path $saida 'icon.png')

Passo '5/5 Programa (Electron)'
Push-Location $PSScriptRoot
try {
    npm install
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar o Electron' }
    if ($SoPreparar) {
        Write-Host "`nPronto! Para abrir sem instalar: cd desktop; npm start" -ForegroundColor Green
    } else {
        npm run instalador
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar o instalador' }
        Write-Host "`nInstalador pronto em desktop\dist" -ForegroundColor Green
    }
} finally { Pop-Location }
