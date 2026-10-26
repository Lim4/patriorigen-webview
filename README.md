# Patriorigen para Android (WebView)

Aplicación Android que abre [patriorigen.es](https://patriorigen.es/) dentro de un WebView.

## Descargar e instalar

Descarga la APK de **Patriorigen WebView** desde la [última versión publicada en Releases](https://github.com/Lim4/patriorigen-webview/releases/latest).

La aplicación requiere **Android 8.0 o posterior** (API 26). Para instalarla, abre el archivo APK descargado y confirma la instalación. Android puede pedirte que autorices la instalación desde esa fuente.

Las versiones publicadas aquí son compilaciones de depuración. Si Android no permite actualizar sobre una versión anterior, desinstala la anterior y vuelve a instalar esta; las compilaciones pueden estar firmadas con claves distintas.

## Funciones

- Abre Patriorigen en la aplicación, sin iniciar un navegador externo.
- Mantiene la interfaz, la cámara, el código manual y la consulta de productos de Patriorigen.
- Incorpora **Google ML Kit Barcode Scanning** como lector adicional. Analiza en el dispositivo los fotogramas del vídeo que ya muestra la web y entrega el código reconocido al flujo de búsqueda existente.

## Cámara y permisos

Android gestiona el permiso de cámara y muestra su solicitud del sistema. El WebView concede acceso de vídeo únicamente a Patriorigen en HTTPS. Si se rechaza el permiso, el código manual sigue disponible y se puede volver a intentar desde la interfaz de la web.

El procesamiento de los fotogramas para leer el código se realiza localmente. Una vez leído, el número se entrega a Patriorigen para la consulta del producto, que puede utilizar servicios de red.

| Permiso | Uso |
| --- | --- |
| **Internet** (`INTERNET`) | Cargar Patriorigen y consultar la información del producto. |
| **Cámara** (`CAMERA`) | Mostrar el vídeo de la cámara y escanear códigos. |

Consulta la [política de privacidad de Patriorigen](https://patriorigen.es/privacidad.html) para más información sobre los datos tratados por la web.

## Compilar

### Desde GitHub Actions

El flujo de GitHub Actions compila automáticamente en cada push al repositorio y también se puede iniciar manualmente desde [Actions](https://github.com/Lim4/patriorigen-webview/actions/workflows/build.yml). Cuando termine correctamente, descarga el artefacto **Patriorigen-WebView-debug** desde la ejecución.

### En local

Se necesita **JDK 17**, **Gradle 8.11.1**, Android SDK Platform **36** y Android SDK Build-Tools **36.0.0**. El Android SDK debe estar instalado y configurado para que Gradle lo encuentre. Desde la raíz del repositorio:

```sh
gradle assembleDebug
```

El APK se genera en:

```
app/build/outputs/apk/debug/app-debug.apk
```

Datos principales del proyecto:

- `applicationId`: `es.patriorigen.webview`
- `minSdk`: 26
- `targetSdk`: 35
- `versionName`: 2.1

## Créditos y licencias

- **Patriorigen:** web, marca e icono pertenecen a sus titulares. Consulta el [aviso legal](https://patriorigen.es/aviso-legal.html).
- **Google ML Kit:** biblioteca de escaneo de códigos de barras integrada en esta aplicación; se distribuye bajo sus propios términos de licencia.
- El código y la documentación originales de este proyecto se distribuyen bajo la **licencia MIT**; consulta [LICENSE](LICENSE). La licencia no cubre la marca, el icono ni los contenidos de Patriorigen, ni las dependencias de terceros, que conservan sus respectivos derechos y licencias.
