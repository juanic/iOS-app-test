# StabilAr-FootX — iOS app (Kotlin Multiplatform)

App iOS (SwiftUI + CoreBluetooth) de la plataforma de estabilometría
**StabilAr-FootX**. Se conecta por Bluetooth Low Energy al servicio
**Nordic UART Service (NUS)** del ESP32-S3, captura la trayectoria del Centro
de Presión (COP) y muestra el peso en vivo.

## Arquitectura

- **`:shared`** — módulo Kotlin Multiplatform con la lógica pura
  (`com.stabilar.core.*`): parser de tramas, conversión a fuerza/kg, COP,
  métricas de estabilometría, mapeo de coordenadas y motores de juego. Se
  compila como framework `Shared` para iOS (Kotlin/Native) y como librería JVM
  para los tests. Comparte el código con la app Android (`BLE_NUS_refact`).
- **`iosApp/`** — app iOS nativa: UI SwiftUI + BLE con CoreBluetooth. El
  proyecto Xcode **no se commitea**: se genera con
  [XcodeGen](https://github.com/yonaskolb/XcodeGen) a partir de
  `project.yml`, y su build invoca `:shared:embedAndSignAppleFrameworkForXcode`
  para compilar el framework Kotlin.
- **`iosApp/iosApp/Ble/`** — `BleManager` (CoreBluetooth) parsea las tramas
  delegando en `FootXBridge` del framework `Shared`, sin duplicar la lógica.

## Cómo compilar

### Local (macOS)
```bash
brew install xcodegen
xcodegen generate --spec iosApp/project.yml --project iosApp
open iosApp/iosApp.xcodeproj
```

### CI (GitHub Actions, desde Windows/Linux)
El workflow `.github/workflows/ios.yml` corre en un runner `macos-15`:
1. Compila el módulo `:shared` y corre los tests de la lógica (`jvmTest`).
2. Genera el proyecto Xcode con XcodeGen.
3. Compila la app con `xcodebuild` (simulador, sin firma).

## Estado actual (primer hito)
- Escaneo de dispositivos NUS por CoreBluetooth.
- Conexión, suscripción a notificaciones y envío de comandos (`CAL_VALUE`).
- Parseo de tramas con el core Kotlin y visualización en vivo de peso (kg) y
  COP (X/Y en mm).
- Tests del core compartidos (parser, coords, juegos) corriendo en CI.
