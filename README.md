# Patriorigen para Android

Aplicación Android ligera que abre https://patriorigen.es/ en un WebView y conserva la interfaz y las funciones que ofrece la web.

## Cámara y permisos

El escáner web usa ZXing y la cámara del navegador. El WebView solicita el permiso de vídeo únicamente para el origen HTTPS de Patriorigen; Android muestra su diálogo de cámara cuando la página intenta iniciar el escáner. Las opciones disponibles y su duración dependen de la versión de Android. Si el permiso queda concedido, se reutiliza; si Android lo revoca, se vuelve a solicitar.

La entrada de código manual sigue funcionando sin permiso de cámara. La selección o captura de fotos para los reportes usa el selector de imágenes de Android.

## Compilación

Requiere JDK 17, Gradle 8.11.1 y Android SDK 36.

```sh
gradle assembleDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.

## Proyecto de referencia

Este proyecto es independiente de [patriorigen-geckoview](https://github.com/Lim4/patriorigen-geckoview). El repositorio GeckoView permanece sin cambios.
