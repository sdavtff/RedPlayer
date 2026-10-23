# Red Player

Player de música original e minimalista: **sem login, sem anúncios, sem senhas**.
Tema vermelho (#E5192B) e preto (#000000).

- Kotlin + Jetpack Compose (Material 3)
- Media3 / ExoPlayer + MediaSessionService (toca em segundo plano, com notificação)
- Retrofit + OkHttp + kotlinx.serialization
- Coil para capas
- Música gratuita via API pública do **Audius** (sem chave) + arquivos de áudio locais (MediaStore)

## Gerar o APK debug
**Opção A – Android Studio:** abra a pasta, aguarde o sync e rode *Build > Build APK(s)*.
Saída: `app/build/outputs/apk/debug/app-debug.apk`.

**Opção B – GitHub Actions (sem instalar nada):** suba a pasta para um repositório seu no GitHub;
o workflow `.github/workflows/build.yml` gera o APK e o disponibiliza em *Actions > Artifacts*.

**Opção C – terminal** (com JDK 17 + Android SDK + Gradle 8.10): `gradle :app:assembleDebug`.
