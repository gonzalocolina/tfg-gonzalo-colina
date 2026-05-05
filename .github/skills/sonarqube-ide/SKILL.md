---
name: sonarqube-ide
description: 'Recoger resultados de análisis estático del plugin SonarQube for IDE (sonarsource.sonarlint-vscode) sin servidor externo. USE FOR: analizar archivos, recoger issues de calidad y seguridad, listar security hotspots, excluir archivos del análisis. DO NOT USE FOR: configurar SonarQube Server/Cloud (usar sonarqube_setup_connected_mode directamente).'
---

# SonarQube for IDE - Recolección de logs y resultados

## Cuándo usar

- Cuando se necesite ejecutar análisis estático sobre archivos del workspace.
- Cuando se quieran recoger issues de calidad de código o seguridad detectados por SonarQube for IDE.
- Cuando se quiera un informe de problemas sin instalar servidor SonarQube ni plugins adicionales.

## Pre-requisitos

- Extensión **SonarQube for IDE** (`sonarsource.sonarlint-vscode`) instalada y activa en VS Code.
- No se necesita SonarQube Server/Cloud para análisis local (solo para Security Hotspots remotos).

## Herramientas MCP disponibles

Antes de usar cualquiera de estas herramientas, **descúbrelas** con:

```
tool_search_tool_regex  pattern: "sonarqube"
```

| Herramienta | Propósito |
|---|---|
| `sonarqube_analyze_file` | Dispara el análisis de un archivo. Los issues se reportan en el panel Problems. |
| `sonarqube_list_potential_security_issues` | Devuelve Security Hotspots y Taint Vulnerabilities de un archivo. Requiere Connected Mode para taint. |
| `sonarqube_exclude_from_analysis` | Excluye archivos/carpetas del análisis mediante glob pattern (ej. `**/test/**`). |
| `sonarqube_setup_connected_mode` | Guía para conectar con SonarQube Server/Cloud (opcional). |

## Procedimiento: Analizar un archivo y recoger resultados

### Paso 1 - Disparar el análisis

```
sonarqube_analyze_file  filePath: "<ruta-absoluta-del-archivo>"
```

El análisis es asíncrono; los issues aparecen en el panel **Problems** de VS Code.

### Paso 2 - Recoger los resultados

Usar `get_errors` para leer el panel Problems:

```
get_errors  filePaths: ["<ruta-absoluta-del-archivo>"]
```

Los problemas reportados por SonarQube aparecen junto con los del compilador/linter.
Para obtener **todos** los problems del workspace (incluidos los de SonarQube):

```
get_errors   (sin parámetros)
```

### Paso 3 - Issues de seguridad (opcional)

```
sonarqube_list_potential_security_issues  filePath: "<ruta-absoluta-del-archivo>"
```

> Nota: Sin Connected Mode solo se obtienen Security Hotspots locales. Taint Vulnerabilities requieren vinculación con SonarQube Server/Cloud.

## Procedimiento: Análisis por lotes (múltiples archivos)

Para analizar varios archivos, repetir el paso 1 por cada archivo y luego recoger todos los resultados de golpe:

1. Llamar `sonarqube_analyze_file` para cada archivo (se pueden lanzar en paralelo).
2. Llamar `get_errors` sin parámetros para recoger todos los issues del workspace.

## Procedimiento: Excluir archivos del análisis

```
sonarqube_exclude_from_analysis  globPattern: "**/node_modules/**"
```

Patrones comunes:
- `**/test/**` - excluir tests
- `**/dist/**` - excluir builds
- `**/*.min.js` - excluir minificados

## Ejemplo completo de flujo

```text
1. tool_search_tool_regex  pattern: "sonarqube"           → descubrir herramientas
2. sonarqube_analyze_file  filePath: "C:/proyecto/src/App.vue"  → disparar análisis
3. get_errors  filePaths: ["C:/proyecto/src/App.vue"]       → leer resultados
4. sonarqube_list_potential_security_issues  filePath: "..."  → issues de seguridad
```

## Notas importantes

- `sonarqube_analyze_file` **no soporta Jupyter Notebooks**.
- Los resultados de `get_errors` mezclan issues de SonarQube con los del compilador/linter nativo. Los mensajes de SonarQube suelen seguir patrones como "Remove this unused...", "A form label must be associated...", etc.
- El análisis local no requiere conexión a internet ni servidor externo.