# Patriorigen-WebView

Aplicación Android experimental que muestra [patriorigen.es](https://patriorigen.es) en Android WebView y utiliza un lector de códigos nativo. Este proyecto es independiente de [patriorigen-geckoview](https://github.com/Lim4/patriorigen-geckoview), que permanece sin cambios.

## Escaneo

El lector nativo usa CameraX y el modelo integrado de ML Kit para códigos EAN-13, EAN-8, UPC-A y UPC-E. Prioriza el sensor trasero con mayor tamaño físico y autofocus cuando el fabricante lo expone; si no, utiliza la cámara trasera predeterminada. Se puede tocar la vista previa para enfocar. La búsqueda manual de la web sigue disponible.

## Descargar

El flujo de GitHub Actions genera un APK de depuración como artefacto. Las compilaciones de prueba también estarán disponibles en la sección [Releases](https://github.com/Lim4/patriorigen-webview/releases).

## Compilar

Se necesita Android Studio o un entorno con JDK 17, Gradle 8.11.1 y Android SDK (plataforma 36).

```sh
gradle assembleDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.

## Permisos

La aplicación necesita conexión a Internet y acceso a la cámara para el lector nativo. El WebView no recibe acceso directo a la cámara.

## Créditos

Patriorigen y su contenido pertenecen a sus respectivos titulares. Android WebView, CameraX y ML Kit se proporcionan bajo sus respectivas licencias.

## Estado

Prueba técnica para comparar tamaño, mantenimiento y compatibilidad de WebView con la versión GeckoView. La versión GeckoView original sigue siendo el proyecto de referencia.
