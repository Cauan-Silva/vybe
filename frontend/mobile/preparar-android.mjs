#!/usr/bin/env node
/*
 * Prepara o projeto Android gerado pelo Capacitor (pasta frontend/android) para o VYBE.
 *
 * A pasta android não fica no Git: o GitHub Actions roda "npx cap add android" a cada build
 * e depois este script, que
 *   1. copia o código nativo (MainActivity e o plugin Tocador, em mobile/android/java);
 *   2. troca os ícones e a tela de abertura do Capacitor pelos do VYBE (mobile/android/res);
 *   3. acrescenta ao AndroidManifest as permissões e o serviço de reprodução de mídia;
 *   4. opcional: grava a versão do app (node mobile/preparar-android.mjs 0.3.0);
 *   5. opcional: --teste faz do APK um app separado ("VYBE Teste", com.cauan.vybe.teste),
 *      que instala ao lado do app final sem conflito de assinatura.
 *
 * Pode rodar mais de uma vez: o que já foi feito não é repetido.
 */
import { cpSync, existsSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const AQUI = dirname(fileURLToPath(import.meta.url))
const ANDROID = join(AQUI, '..', 'android')
const APP = join(ANDROID, 'app')
const PRINCIPAL = join(APP, 'src', 'main')
const PACOTE = join('com', 'cauan', 'vybe')

function falhar(mensagem) {
  console.error(`preparar-android: ${mensagem}`)
  process.exit(1)
}

if (!existsSync(PRINCIPAL)) {
  falhar('pasta android não encontrada. Rode antes: npx cap add android')
}

// 1. Código nativo
cpSync(join(AQUI, 'android', 'java', PACOTE), join(PRINCIPAL, 'java', PACOTE), { recursive: true })
console.log('✓ código nativo (MainActivity, TocadorPlugin, TocadorService)')

// 2. Ícones e tela de abertura. As splash.png do Capacitor (uma por tamanho e orientação)
// teriam prioridade sobre a nossa splash.xml, então saem antes.
const RES = join(PRINCIPAL, 'res')
for (const pasta of readdirSync(RES)) {
  if (!pasta.startsWith('drawable')) continue
  const splash = join(RES, pasta, 'splash.png')
  if (existsSync(splash)) rmSync(splash)
}
cpSync(join(AQUI, 'android', 'res'), RES, { recursive: true })
console.log('✓ ícones e tela de abertura')

// 3. AndroidManifest
const caminhoManifesto = join(PRINCIPAL, 'AndroidManifest.xml')
let manifesto = readFileSync(caminhoManifesto, 'utf8')

const PERMISSOES = [
  'android.permission.INTERNET',
  'android.permission.FOREGROUND_SERVICE',
  'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK',
  'android.permission.WAKE_LOCK',
  'android.permission.POST_NOTIFICATIONS',
]
const faltando = PERMISSOES.filter((p) => !manifesto.includes(`"${p}"`))
if (faltando.length) {
  const linhas = faltando.map((p) => `    <uses-permission android:name="${p}" />`).join('\n')
  manifesto = manifesto.replace(/(\s*)<application\b/, `\n${linhas}\n$1<application`)
}

if (!manifesto.includes('.TocadorService')) {
  const servico = [
    '        <!-- Mantém a música tocando com a tela apagada (plugin Tocador). -->',
    '        <service',
    '            android:name=".TocadorService"',
    '            android:exported="false"',
    '            android:foregroundServiceType="mediaPlayback" />',
  ].join('\n')
  if (!manifesto.includes('</application>')) falhar('AndroidManifest.xml sem </application>')
  manifesto = manifesto.replace(/[ \t]*<\/application>/, `${servico}\n    </application>`)
}
writeFileSync(caminhoManifesto, manifesto)
console.log('✓ AndroidManifest (permissões e serviço de mídia)')

// 4. Versão: "0.3.0" vira versionName "0.3.0" e versionCode 300 (1.2.3 → 10203).
const argumentos = process.argv.slice(2)
const TESTE = argumentos.includes('--teste')
const versao = (argumentos.find((a) => !a.startsWith('--')) ?? '').replace(/^v/, '')
if (versao) {
  const partes = versao.match(/^(\d+)\.(\d+)\.(\d+)/)
  if (!partes) falhar(`versão inválida: "${versao}" (use 0.3.0)`)
  const [, maior, menor, correcao] = partes.map(Number)
  const codigo = maior * 10000 + menor * 100 + correcao || 1
  const caminhoGradle = join(APP, 'build.gradle')
  let gradle = readFileSync(caminhoGradle, 'utf8')
  if (!/versionCode\s+\d+/.test(gradle) || !/versionName\s+"[^"]*"/.test(gradle)) {
    falhar('não achei versionCode/versionName em android/app/build.gradle')
  }
  gradle = gradle
    .replace(/versionCode\s+\d+/, `versionCode ${codigo}`)
    .replace(/versionName\s+"[^"]*"/, `versionName "${versao}"`)
  writeFileSync(caminhoGradle, gradle)
  console.log(`✓ versão ${versao} (código ${codigo})`)
}

// 5. APK de teste: outro id e outro nome, para conviver com o app final no mesmo celular.
if (TESTE) {
  const caminhoGradle = join(APP, 'build.gradle')
  const gradle = readFileSync(caminhoGradle, 'utf8')
  if (!gradle.includes('applicationId "com.cauan.vybe"')) falhar('não achei applicationId em android/app/build.gradle')
  writeFileSync(caminhoGradle, gradle.replace('applicationId "com.cauan.vybe"', 'applicationId "com.cauan.vybe.teste"'))
  const caminhoTextos = join(RES, 'values', 'strings.xml')
  if (existsSync(caminhoTextos)) {
    const textos = readFileSync(caminhoTextos, 'utf8').replace(/>VYBE</g, '>VYBE Teste<')
    writeFileSync(caminhoTextos, textos)
  }
  console.log('✓ APK de teste (VYBE Teste, com.cauan.vybe.teste)')
}
