# Manual de usuario

La aplicación ayuda a coordinar partidas presenciales del modo Multijugador épico de *Marvel Champions*. No sustituye al juego físico: mantiene el estado global del evento y comunica los cambios entre las mesas.

Hay dos perfiles:

- **Organizador:** prepara el evento y supervisa la partida desde una interfaz orientada a escritorio, así como ayudar a otro organizador a organizar su evento.
- **Jugador:** representa a una mesa y registra su progreso desde una interfaz adaptada a móvil.

La apariencia clara u oscura se adapta a la preferencia del dispositivo.

## Acceso

La página principal ofrece dos entradas:

- **Organizar una partida**, que abre el acceso del organizador.
- **Unirse a la partida**, que abre el formulario del jugador.

En un entorno local están disponibles en:

- Organizador: [http://localhost:5175/organizer/login](http://localhost:5175/organizer/login)
- Jugador: [http://localhost:5175/player/join](http://localhost:5175/player/join)



## Organizador



### Crear una cuenta e iniciar sesión

Para administrar eventos es necesario disponer de una cuenta.

1. En la pantalla de acceso, introduce el usuario y la contraseña.
2. Si todavía no tienes una cuenta, selecciona **Registrarse**.
3. Indica un nombre de usuario, una contraseña y, opcionalmente, un correo electrónico.
4. Completa el registro y accede con las credenciales creadas.

En desarrollo existe el usuario inicial `organizer`. Su contraseña depende de `APP_SEED_ORGANIZER_PASSWORD`; el valor incluido en `.env.example` es `organizer`.

### Pantalla Mis Eventos

Después de iniciar sesión, **Mis Eventos** reúne los eventos propios y aquellos en los que se participa como coorganizador.

Cada tarjeta muestra la información principal:

- nombre y estado del evento;
- rol del usuario;
- número de jugadores;
- límite de tiempo;
- código de acceso para los jugadores.

Desde esta pantalla también se puede:

- crear un evento;
- gestionar el catálogo de villanos;
- gestionar las cartas inter-nodo;
- revisar invitaciones de coorganización.



### Catálogo de villanos

El catálogo contiene los villanos disponibles para reutilizarlos en distintos eventos. Incluye elementos predefinidos del sistema, como Loki, y admite villanos personalizados.

Al crear un villano se indica:

- nombre;
- cálculo de puntos de vida;
- cálculo del límite de amenaza;
- valores fijos o multiplicadores necesarios para esas fórmulas.

Los villanos predefinidos no se pueden editar. Los personalizados se administran desde el catálogo del organizador.

### Catálogo de cartas inter-nodo

Las cartas inter-nodo representan elementos cuyo estado se comparte dentro de un nodo. Cada definición incluye:

- nombre;
- categoría: esbirro o plan secundario;
- forma de calcular su métrica, ya sea un valor fijo o dependiente de las mesas;
- imagen opcional.

Los esbirros utilizan puntos de vida y los planes secundarios utilizan amenaza. Crear una definición no la añade automáticamente a todos los eventos: las cartas disponibles se eligen al configurar cada uno.

### Crear un evento

Selecciona **Crear nuevo evento** e introduce:

1. el nombre del evento;
2. el límite de tiempo en minutos; un valor de `0` desactiva el temporizador;
3. el villano;
4. las cartas inter-nodo que podrán aparecer.

La configuración se puede modificar mientras el evento permanece en borrador.

### Panel del evento

El panel resume la preparación y el estado actual:

- código de acceso;
- jugadores y mesas registrados;
- tiempo límite;
- villano y cálculos de vida y amenaza;
- cartas activadas para el evento;
- accesos a la distribución de nodos, coorganizadores y partida.

Comparte el código del evento con los representantes de las mesas para que puedan registrarse.

### Configurar nodos y mesas

Antes de iniciar la partida, las mesas deben estar agrupadas en nodos.

1. Crea los nodos necesarios.
2. Espera a que los representantes registren sus mesas o comprueba las ya disponibles.
3. Arrastra cada mesa al nodo correspondiente.
4. Utiliza **Asignar pendientes** si quieres distribuir automáticamente las mesas sin asignar.
5. Utiliza **Reorganizar todas** para calcular de nuevo la distribución completa.

No se puede iniciar la partida mientras haya mesas sin asignar.

### Seleccionar villano y cartas

Las secciones **Villanos** y **Cartas Inter-Nodo** permiten revisar la selección del evento sin alterar los catálogos generales.

Estos elementos solo se pueden cambiar antes de iniciar la partida. Revisa las fórmulas calculadas, el número de mesas y los valores resultantes antes de continuar.

### Coorganizadores

El organizador principal puede delegar parte de la supervisión en otros usuarios registrados.

1. Abre la sección **Coorganizadores**.
2. Busca o indica el usuario al que quieres invitar.
3. Asigna una o varias responsabilidades:
  - vida del villano;
  - amenaza;
  - temporizador.
4. Envía la invitación.

El destinatario puede aceptarla o rechazarla desde **Mis invitaciones**. El organizador principal puede revocar invitaciones pendientes o accesos ya concedidos.

Los permisos limitan qué controles puede utilizar cada coorganizador durante la partida.

### Iniciar y supervisar la partida

Cuando la configuración esté completa, abre **Partida** e inicia el evento. El panel muestra:

- estado de la conexión en tiempo real;
- vida actual y máxima del villano;
- amenaza actual y límite del plan principal;
- temporizador;
- nodos, mesas y actividad reciente.

Los cambios enviados por las mesas se reflejan automáticamente. Si se interrumpe la conexión en tiempo real, comprueba el indicador antes de registrar acciones adicionales.

### Temporizador

Si el evento tiene un límite de tiempo, el organizador o un coorganizador autorizado puede:

- pausar y reanudar la cuenta;
- restar 5 minutos;
- añadir 5 o 15 minutos.

Cada ajuste queda registrado en el historial.

### Acciones delegadas y correcciones

El panel permite corregir errores sin alterar entradas anteriores del registro.

#### Registro en nombre de una mesa

Selecciona la mesa, indica una cantidad y añade un motivo si es necesario. Se puede:

- registrar o revertir daño al villano;
- añadir o retirar amenaza.

Estas acciones modifican el valor actual en la cantidad indicada y quedan atribuidas a la mesa.

#### Corrección global

Una corrección global establece directamente un valor absoluto de vida o amenaza y no se atribuye a una mesa.

Por ejemplo, con un villano en 50 puntos de vida:

- registrar 10 puntos de daño deja el valor en 40;
- corregir la vida a 30 establece directamente el valor en 30.



### Registro de acciones

La sección de registro conserva las operaciones que han modificado la partida:

- daño y amenaza;
- ajustes y correcciones;
- cambios del temporizador;
- aparición y resolución de cartas;
- inicio y final de la partida.

Las entradas muestran la hora, el actor, la mesa cuando corresponda, los valores anterior y posterior y las notas asociadas. Se pueden filtrar por mesa y tipo de acción.

Una entrada no se edita ni se elimina. Cuando una operación admite reversión, el sistema crea una acción adicional que deja constancia de la corrección.

### Estadísticas finales

Al terminar la partida, **Estadísticas** presenta:

- resultado y causa de finalización;
- duración;
- daño total y amenaza final;
- actividad y correcciones;
- evolución temporal;
- rendimiento por mesa;
- protagonistas, como la mesa más activa o la que realizó el golpe final.

Las estadísticas se calculan a partir del registro de acciones del evento.

## Jugador



### Unirse a una partida

El representante de cada mesa abre el acceso de jugador e introduce:

- el código del evento o el código de una mesa existente;
- un identificador de mesa, si está creando una nueva mediante el código del evento;
- el número de jugadores de la mesa, entre 1 y 4.

Al acceder por primera vez se crea la mesa y el dispositivo queda identificado como su representante. Utilizar el código específico de una mesa permite volver a entrar en ella.

Conserva el código necesario para recuperar el acceso si se cierra la sesión o se cambia de dispositivo.

### Sala de espera

Antes del inicio, la interfaz muestra el estado de espera y las mesas registradas. La partida no estará disponible hasta que:

1. el organizador asigne la mesa a un nodo;
2. todas las mesas queden correctamente distribuidas;
3. el organizador inicie el evento.

La pantalla se actualiza cuando cambia el estado.

### Vista de partida

Durante el evento, el jugador puede consultar:

- temporizador global;
- vida del villano;
- amenaza del plan principal;
- distribución de nodos y mesas;
- nodo y mesa propios.

La interfaz ofrece tres acciones principales:

- **Reportar Daño:** resta vida al villano.
- **Amenaza:** incrementa la amenaza del plan principal.
- **Cartas Inter-Nodo:** abre el estado compartido de las cartas del nodo.

Antes de confirmar una acción, comprueba la cantidad introducida. Si hay un error, comunícaselo al organizador para que registre la reversión o corrección correspondiente.

### Cartas inter-nodo

Para registrar una carta:

1. abre **Cartas Inter-Nodo**;
2. selecciona una definición disponible;
3. pulsa **Me ha aparecido esta carta**.

Las cartas activas muestran su progreso:

- en un esbirro se registra daño hasta reducir su vida;
- en un plan secundario se retira amenaza hasta resolverlo.

Una vez resuelta, la carta pasa al listado de cartas derrotadas del nodo. Todas las mesas del mismo nodo comparten su estado.

### Fin de la partida

El evento termina cuando se cumple una condición de finalización, por ejemplo:

- la vida del villano llega a cero;
- la amenaza alcanza su límite;
- se agota el temporizador.

La vista final informa del resultado, la causa, la duración y los totales de daño y amenaza. El organizador dispone de un desglose más amplio en la sección de estadísticas.

## Resolución de problemas



### La pantalla no se actualiza

Comprueba la conexión de red y el indicador de tiempo real. Si continúa desconectado, recarga la página y vuelve a entrar con el código de la mesa.

### No se puede iniciar el evento

Verifica que exista un villano seleccionado y que todas las mesas estén asignadas a un nodo.

### Una acción tiene una cantidad incorrecta

No repitas la operación con valores negativos salvo que la interfaz ofrezca expresamente esa acción. Solicita al organizador una reversión, un registro delegado inverso o una corrección global, según corresponda.

### No aparece una invitación de coorganizador

Confirma que la invitación se envió al nombre de usuario correcto y actualiza **Mis invitaciones**. El destinatario debe haber creado previamente una cuenta.