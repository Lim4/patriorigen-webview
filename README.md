# Patriorigen-WebView

Aplicación Android experimental que abre [patriorigen.es](https://patriorigen.es) usando el WebView incluido en Android. Este proyecto es independiente de [patriorigen-geckoview](https://github.com/Lim4/patriorigen-geckoview), que permanece sin cambios.

## Descargar

Las compilaciones de prueba estarán disponibles en la sección [Releases](https://github.com/Lim4/patriorigen-webview/releases). Por ahora, el flujo de GitHub Actions genera un APK de depuración como artefacto.

## Compilar

Se necesita Android Studio o un entorno con JDK 17, Gradle 8.11.1 y Android SDK (plataforma 36).

```sh
gradle assembleDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.

## Permisos

La aplicación solicita conexión a Internet. Cámara y micrófono se solicitan al sistema solo cuando la web los necesita; el acceso web se limita a solicitudes HTTPS de `patriorigen.es` y requiere confirmación del usuario.

## Créditos

Patriorigen y su contenido pertenecen a sus respectivos titulares. Android WebView se proporciona como parte de Android.

## Estado

Prueba técnica para comparar el tamaño, mantenimiento y compatibilidad de WebView con la versión GeckoView. La versión GeckoView original sigue siendo el proyecto de referencia.
