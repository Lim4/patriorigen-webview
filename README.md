# Patriorigen para Android (WebView)

Aplicación Android que abre [patriorigen.es](https://patriorigen.es/) dentro de un WebView.

## Descargar e instalar

Descarga la APK de **Patriorigen WebView** desde la [última versión publicada en Releases](https://github.com/Lim4/patriorigen-webview/releases/latest).

La aplicación requiere **Android 8.0 o posterior** (API 26). Para instalarla, abre el archivo APK descargado y confirma la instalación. Android puede pedirte que autorices la instalación desde esa fuente.

Las APK de las nuevas compilaciones se firman como versión `release` con una clave persistente guardada como secreto de GitHub Actions. Android permite instalarlas encima de una versión anterior si conserva el mismo `applicationId`, la misma clave de firma y un `versionCode` superior. El proyecto conserva `es.patriorigen.webview` y aumenta `versionCode` para cada actualización.

**Migración desde la APK debug actual:** la clave release es distinta de la clave debug. Android no permite cambiar la firma durante una actualización, así que hay que desinstalar la app debug una sola vez e instalar la primera APK release. A partir de ahí, las APK release firmadas con la misma clave se podrán instalar encima, sin desinstalar. La desinstalación inicial borra los datos locales de la aplicación.

## Preparar la firma persistente (una sola vez)

Antes de ejecutar el flujo de Actions, crea una clave privada de firma y guárdala de forma segura fuera del repositorio. No la subas ni la compartas: perderla impide firmar futuras actualizaciones compatibles.

Con JDK 17, genera un almacén de claves:

```sh
keytool -genkeypair -v -keystore patriorigen-release.jks -alias patriorigen -keyalg RSA -keysize 2048 -validity 10000
```

En GitHub, abre **Settings → Secrets and variables → Actions → New repository secret** y crea estos cuatro secretos:

- `ANDROID_KEYSTORE_BASE64`: contenido del archivo JKS codificado en Base64 (en PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("patriorigen-release.jks")) | Set-Clipboard`).
- `ANDROID_KEYSTORE_PASSWORD`: contraseña del almacén.
- `ANDROID_KEY_ALIAS`: el alias usado al crear la clave (`patriorigen` en el ejemplo).
- `ANDROID_KEY_PASSWORD`: contraseña de la clave.

No incluyas las contraseñas ni el almacén en el repositorio. El flujo falla con un mensaje claro si faltan secretos, en lugar de generar una APK que no pueda actualizar la versión instalada.

## Funciones

- Abre Patriorigen en la aplicación, sin iniciar un navegador externo.
- Mantiene la interfaz, la cámara, el código manual y la consulta de productos de Patriorigen.
- Incorpora **Google ML Kit Barcode Scanning** como lector adicional. Analiza en el dispositivo los fotogramas del vídeo que ya muestra la web y entrega el código reconocido al flujo de búsqueda existente.

## Cámara y permisos

Android solicita el permiso de cámara al iniciar la aplicación si todavía no está concedido. Si ya se concedió, el sistema no vuelve a mostrar el diálogo. El WebView concede acceso de vídeo únicamente a Patriorigen mediante HTTPS. Si se deniega el permiso, el código manual sigue disponible y se puede volver a intentar desde la interfaz web. Si Android ya no permite mostrar otra solicitud, el permiso se puede activar en los ajustes de la aplicación.

El procesamiento de los fotogramas para leer el código se realiza localmente. Una vez leído, el número se entrega a Patriorigen para la consulta del producto, que puede utilizar servicios de red.

| Permiso | Uso |
| --- | --- |
| **Internet** (`INTERNET`) | Cargar Patriorigen y consultar la información del producto. |
| **Cámara** (`CAMERA`) | Mostrar el vídeo de la cámara y escanear códigos. |

Consulta la [política de privacidad de Patriorigen](https://patriorigen.es/privacidad.html) para más información sobre los datos tratados por la web.

## Compilar

### Desde GitHub Actions

El flujo de GitHub Actions compila automáticamente en cada push al repositorio y también se puede iniciar manualmente desde [Actions](https://github.com/Lim4/patriorigen-webview/actions/workflows/build.yml). Descarga el artefacto **Patriorigen-WebView-release** desde la ejecución correcta. Asegúrate de haber configurado los cuatro secretos de firma indicados arriba.

### En local

Se necesita **JDK 17**, **Gradle 8.11.1**, Android SDK Platform **36** y Android SDK Build-Tools **36.0.0**. El Android SDK debe estar instalado y configurado para que Gradle lo encuentre. Desde la raíz del repositorio:

```sh
gradle assembleDebug
```

Este comando genera una APK debug para desarrollo, que no podrá actualizar la APK release instalada en el móvil. El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.

Datos principales del proyecto:

- `applicationId`: `es.patriorigen.webview`
- `minSdk`: 26
- `targetSdk`: 35
- `versionName`: 2.1

## Créditos y licencias

- **Patriorigen:** web, marca e icono pertenecen a sus titulares. Consulta el [aviso legal](https://patriorigen.es/aviso-legal.html).
- **Google ML Kit:** biblioteca de escaneo de códigos de barras integrada en esta aplicación; se distribuye bajo sus propios términos de licencia.
- El código y la documentación originales de este proyecto se distribuyen bajo la **licencia MIT**; consulta [LICENSE](LICENSE). La licencia no cubre la marca, el icono ni los contenidos de Patriorigen, ni las dependencias de terceros, que conservan sus respectivos derechos y licencias.
