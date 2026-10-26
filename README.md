# Patriorigen para Android

Aplicación Android que abre https://patriorigen.es/ en un WebView y conserva la interfaz y las funciones de la web.

## Cámara

Al iniciar, Android solicita el permiso de cámara si aún no está concedido. El WebView da acceso de vídeo solo al origen HTTPS de Patriorigen. Si el permiso se rechaza al inicio, el código manual permanece disponible y se puede volver a intentar desde el botón de cámara. Las decisiones de permiso y las opciones de duración las gestiona Android en cada versión.

El vídeo de la página permanece oculto hasta que recibe una imagen real de la cámara, para evitar el icono gris del reproductor durante el inicio.

## Compilación

Requiere JDK 17, Gradle 8.11.1 y Android SDK 36.

```sh
gradle assembleDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.

El proyecto es independiente de [patriorigen-geckoview](https://github.com/Lim4/patriorigen-geckoview), que permanece sin cambios.
