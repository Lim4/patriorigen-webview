# Patriorigen para Android (WebView)

Aplicación Android que abre [patriorigen.es](https://patriorigen.es/) dentro de un WebView. Conserva la interfaz y el flujo de consulta de la web; no añade una pantalla de cámara propia.

## Descargar e instalar

La aplicación todavía no tiene versiones publicadas en **Releases**. Para descargar el APK de prueba:

1. Abre [GitHub Actions](https://github.com/Lim4/patriorigen-webview/actions/workflows/build.yml).
2. Selecciona la última ejecución correcta de **Build WebView APK**.
3. Descarga el artefacto **Patriorigen-WebView-debug** y extrae el archivo APK.
4. Abre el APK en el móvil y confirma la instalación. Android puede pedir permiso para instalar aplicaciones desde esa fuente.

El artefacto contiene una compilación de depuración. Para descargarlo, necesitas acceso al repositorio.

La aplicación requiere **Android 8.0 o posterior** (API 26).

## Funciones

- Abre Patriorigen en la aplicación, sin iniciar un navegador externo.
- Mantiene la interfaz, la cámara, el código manual y la consulta de productos de Patriorigen.
- Incorpora **Google ML Kit Barcode Scanning** como lector adicional. Analiza en el dispositivo los fotogramas del vídeo que ya muestra la web y entrega el código reconocido al flujo de búsqueda existente.
- Reconoce EAN-13, EAN-8, UPC-A, UPC-E, Code 128 e ITF.
- Incluye el modelo de ML Kit en la aplicación, por lo que no tiene que descargarlo al primer escaneo.
- Adapta el contenido a las barras de estado y navegación de Android.

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

El repositorio compila el APK en cada cambio y permite iniciar una compilación manual desde [Actions](https://github.com/Lim4/patriorigen-webview/actions/workflows/build.yml). Descarga el artefacto **Patriorigen-WebView-debug** de una ejecución completada correctamente.

### En local

Se necesita **JDK 17**, **Gradle 8.11.1**, Android SDK Platform **36** y Android SDK Build-Tools **36.0.0**. Desde la raíz del repositorio:

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
- **Google ML Kit:** biblioteca de escaneo de códigos de barras integrada en esta aplicación.
- Este cliente Android es independiente de [Patriorigen GeckoView](https://github.com/Lim4/patriorigen-geckoview) y no está afiliado oficialmente con Mozilla ni con los titulares de Patriorigen.
- Este repositorio no incluye actualmente un archivo `LICENSE`; por tanto, no declara una licencia de distribución para el código del cliente.
