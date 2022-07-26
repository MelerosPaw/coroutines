package meleros.paw.corrutinas

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import meleros.paw.corrutinas.di.DIManager
import java.lang.NumberFormatException
import javax.inject.Inject
import kotlin.concurrent.thread

class MainViewModel : BaseViewModel() {

    @Inject
    lateinit var hipotecaUseCase: HipotecaUseCase

    init {
        DIManager.getAppComponent().inject(this)
    }

    fun corrutina() {
        viewModelScope.launch(Dispatchers.Default) {
            val resultado = operacionBloqueante()
            _textLiveData.postValue(resultado.toString())
            _loadingLiveData.postValue(false)
        }
    }

    fun hilosVsCorrutinas() {
        thread {
            val resultado = operacionBloqueante()
            _textLiveData.postValue(resultado.toString())
            _loadingLiveData.postValue(false)
        }
    }

    // Aquí se ejecutan cosas costosas de forma asíncrona, pero escrito de forma síncrona.
    // La ejecución tiene lugar en un hilo
    // Suspender corrutina != bloquear hilo
    // Tras lanzar (no ejecutar, lanzar) una corrutina, la ejecución del resto del código continúa
    // La ejecución de la corrutina sucede después (asíncrona).
    fun estoEsUnaCorrutina() {
        // MAIN
        GlobalScope.launch {
            printThreadName("launch sin dispatcher")
        }
    }

    fun queHiloEjecutaCadaDispatcher() {
        GlobalScope.launch {
            // Sin indicar dispatcher, se usa el del GlobalScope.
            // Aquí dentro estamos en un hilo secundario.
            printThreadName("launch sin dispatcher")
        }

        GlobalScope.launch(Dispatchers.Main) {
            // Principal.
            printThreadName("launch con Dispatchers.Main")
        }

        GlobalScope.launch(Dispatchers.Default) {
            // Secundario para operaciones de uso intensivo de CPU
            printThreadName("launch con Dispatchers.Default")
        }

        GlobalScope.launch(Dispatchers.IO) {
            // Secundario para operaciones de entrada-salida
            printThreadName("launch con Dispatchers.IO")
        }

        GlobalScope.launch(Dispatchers.Unconfined) {
            // Olvida esto.
            // Secundario porque empieza con el dispatcher actual y va mutando.
            printThreadName("launch con Dispatchers.Unconfined")
        }

        GlobalScope.launch(newSingleThreadContext("Mi hilo")) {
            // "Mi hilo".
            printThreadName("""launch que lanza "Mi hilo"""")
        }

        CoroutineScope(Dispatchers.Main).launch(Dispatchers.Default) {
            // Secundario porque el dispatcher de launch sobrescribe al del scope
            printThreadName("launch desde un scope propio")
        }

        viewModelScope.launch {
            // Principal porque es el dispatcher por defecto de viewModelScope
            printThreadName("launch desde un viewModelScope")
        }
    }

    // La ejecución puede ser concurrente siempre que vaya en hilos diferentes, pero secuencial si van en el mismo
    fun ejecucionSincronaYConcurrente() {
        GlobalScope.launch(Dispatchers.Main) {
            repeat(100) { printWithTag("Launch ${it + 1}") }
        }

        repeat(100) { printWithTag("Main ${it + 1}") }
    }

    // Cuando hay más de una corrutina, sucede igual, son concurrentes entre sí o secuenciales según el hilo
    fun ejecucionSincronaYConcurrenteAnidada() {
        val contexto = newSingleThreadContext("El mismo hilo")

        GlobalScope.launch(contexto) {
            printThreadName("launch 1")

            launch(contexto) {
                printThreadName("launch 2")
                repeat(100) { printWithTag("Launch 2.${it + 1}") }
            }

            repeat(100) { printWithTag("Launch 1.${it + 1}") }
        }

        repeat(100) { printWithTag("Main ${it + 1}") }
    }

    // Si lanzamos corrutinas en bucle, siguen siendo asíncronas y concurrentes según el hilo
    fun concurrenciaEnBucles() {
        repeat(10) {
            viewModelScope.launch(Dispatchers.Default) {
                printThreadName("launch ${it + 1}")
            }
        }
    }

    fun launchAsyncIgualDeConcurrentes() {
        viewModelScope.launch {

            launch() {
                delay(5000L)
                printWithTag("Corrutina 1 finalizado")
            }

            launch() {
                delay(5000L)
                printWithTag("Corrutina 2 finalizado")
            }
        }
    }

    // Solo podemos orquestar un poco esperando al resultado con async
    // La clave está en pensar si hay dos o más tareas independientes entre sí y, si es así, meterlas en launch
    // diferentes. Y si además de eso, hacen falta sus resultados después, no usar launch, sino async.
    fun esperarAlResultado() {

        viewModelScope.launch {
            delay(3000L)
            val res1 = llamaAUnWS()

            delay(3000L)
            val res2 = llamaAOtroWS()

            delay(3000L)
            val res3 = llamaAOtroWSMas()

            printWithTag("${res1 + res2 + res3}")
        }
    }

    // Funciones de suspensión
    fun hacerAlgoSuspendiendo() {
        viewModelScope.launch {
            delay(3000L)

            val deferredResult = async(Dispatchers.Default) {
                printThreadName("1")
                Thread.sleep(4000L)
                7
            }

            val deferredResult2 = async(Dispatchers.Default) {
                printThreadName("2")
                Thread.sleep(4000L)
                7
            }

            _textLiveData.value = (deferredResult.await() + deferredResult2.await()).toString()
            _loadingLiveData.value = false
        }
    }

    fun operacionSoloDeIda() {
        viewModelScope.launch(Dispatchers.Default) {
            val resultado = operacionBloqueante()
            printWithTag(resultado.toString())
        }
    }

    fun operacionDeIdaYVuelta() {
        viewModelScope.launch(Dispatchers.Default) {
            val resultado = operacionBloqueante()
            _textLiveData.postValue(resultado.toString())
            _loadingLiveData.postValue(false)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun delayEstaConfabuladoConElMain() {
        printWithTag("Go!")

        viewModelScope.launch(Dispatchers.Default) {
            (1..50000).forEach {
                delay(300)
                printWithTag("¡Palabra $it encontrada!")
            }
        }

        viewModelScope.launch {
            for (i in 1000 downTo 1) {
                printWithTag("${i}s")
                Thread.sleep(100)
            }
        }
    }

    fun bloquearHiloPrincipal() {
        viewModelScope.launch {
            operacionBloqueante()
            _loadingLiveData.value = false
        }
    }

    fun corrutinaConTrampa() {
        viewModelScope.launch {
            val resultado1: Float = operacionMenosBloqueante()
            _textLiveData.value = "Paso 1: $resultado1"
            val resultado2: Float = operacionMenosBloqueante()
            _textLiveData.value = "Paso 2: $resultado2"
            val resultado3: Float = operacionMenosBloqueante()
            _textLiveData.value = "Fin: $resultado3"
            _loadingLiveData.value = false
        }
    }

    fun uiNoUi() {
        viewModelScope.launch(Dispatchers.IO) {
            printThreadName("Primero")
            _textLiveData.value = "Texto cambiado"
            printThreadName("Y luego")
        }
    }

    fun unJob() {
        val job: Job = viewModelScope.launch {
        }
    }

    fun obtenerVariosResultados() {
        viewModelScope.launch {
            medirTiempo {
                val valor1: Deferred<Int> = async(Dispatchers.Default) {
                    printWithTag("Calculando el valor 1")
                    delay(2000)
                    1
                }

                val valor2: Deferred<Int> = async(Dispatchers.Default) {
                    printWithTag("Calculando el valor 2")
                    delay(2000)
                    2
                }

                delay(3000)
                printWithTag("Solicitando el resultado")
                val resultado = valor1.await() + valor2.await()

                printWithTag("El resultado es $resultado")
                _textLiveData.value = resultado.toString()
                _loadingLiveData.value = false
            }
        }
    }

////////////////// LO JUGOSO //////////////////////////////

    fun suspension() {
        viewModelScope.launch(Dispatchers.Default) {
            printThreadName("Launch")
            val resultado: Float = suspenderUltracostosa()
            _textLiveData.postValue("$resultado")
            _loadingLiveData.postValue(false)
        }
    }


    suspend fun suspenderUltracostosa(): Float {
        return withContext(Dispatchers.Default) {
            printThreadName("With context")
            operacionBloqueante()
        }
    }

    private lateinit var jobPadre: Job
    private lateinit var jobHijo: Job

    fun corrutinaHija() {
        jobPadre = viewModelScope.launch(Dispatchers.Default) {

            jobHijo = launch {
                // Esta corrutina es hija de la de fuera
                printWithTag("Tengo ${jobPadre.children.count()} hija")
            }
        }
    }

    private lateinit var jobPadre2: Job

    fun corrutinaConVariosHijos() {

        jobPadre2 = viewModelScope.launch(Dispatchers.Default) {
            delay(2000L) // Tiempo para que se inicialice el lateinit
            printWithTag("Inicio de padre: ${jobPadre2.children.count()} hijos")

            launch {
                delay(6000L)
                printWithTag("1.ª: ${jobPadre2.children.count()} hijos")
            }

            launch {
                delay(4000L)
                printWithTag("2.ª: ${jobPadre2.children.count()} hijos")
            }

            launch {
                delay(2000L)
                printWithTag("3.ª: ${jobPadre2.children.count()} hijos")
            }

            printWithTag("Fin de padre: ${jobPadre2.children.count()} hijos")
            delay(3000L)
            jobPadre2.cancel()
        }

        jobPadre2.invokeOnCompletion { printWithTag("Se acabó") }
    }

    lateinit var jobPadre3: Job

    fun romperLaConcurrenciaEstructurada() {
        jobPadre3 = viewModelScope.launch(Dispatchers.Default) {

            // El coroutineScope this no es viewModelScope
            // Por lo tanto, esto una hija; es una hermana
            launch(Dispatchers.Default) {
                while (true) {
                    delay(1000L)
                    printWithTag("Dentro y estoy viva")
                }
            }

            while (true) {
                printWithTag("Fuera y estoy viva")
                delay(1000L)
            }
        }
    }

    fun cancelarJob() {
        jobPadre3.cancel()
    }

    fun cuantaConcurrenciaHayEnLasCorrutinas() {
        viewModelScope.launch(Dispatchers.Default) {
            repeat(10) {
                async {
                    val coroutineNumber = it + 1
                    printThreadName("S $coroutineNumber")
                    printThreadName("E $coroutineNumber")
                }
            }
        }
    }

    fun ejecucionSinConfinar() {
        viewModelScope.launch(Dispatchers.Unconfined) {
            printThreadName("La corrutina")

            withContext(Dispatchers.IO) {
                printThreadName("El primer withContext()")
            }

            printThreadName("Tras el primer withContext(), la corrutina")

            withContext(Dispatchers.Main) {
                printThreadName("El segundo withContext()")
            }

            printThreadName("Tras el segundo withContext(), la corrutina")
        }
    }

    fun logicaDeNegocioHipoteca() {
        val miDinero = 50_000.0

        viewModelScope.launch {
            val texto = if (hipotecaUseCase(miDinero)) {
                "Compra!!!"
            } else {
                "Te va' comé una mierda"
            }

            _textLiveData.value = texto
            _loadingLiveData.value = false
        }
    }

    fun logicaDeNegocioTelefonos() {
        val useCaseInyectado: ObtenerTelefonosUseCase = ObtenerTelefonosCaseImpl()

        viewModelScope.launch {
            medirTiempo {
                val nombres = listOf("Paco", "Pepe", "Paula", "Petra")
                val telefonos = useCaseInyectado.invoke(nombres)
                _textLiveData.value = telefonos.joinToString(", ")
            }
        }
    }

    fun asyncs() {
        viewModelScope.launch {
            printThreadName("fuera")

            repeat(5) {
                launch {
                    printThreadName("dentro ${it + 1}")
                    Thread.sleep(4000L)
                }
            }

            (1..500_000_000L).forEach {
                if (it > 1 && it % 100_000_000 == 0L) {
                    printThreadName("bucle ${it}")
                }
            }

            printThreadName("fuera de nuevo")
        }
    }

    fun tryCatch() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                "Texto".toInt()
            } catch (e: Throwable) {
                printWithTag("Error controlado")
            }
        }
    }

    fun exceptionHandler() {
        val handler = CoroutineExceptionHandler { coroutineContext, throwable ->
            printWithTag("${throwable is NumberFormatException} y $coroutineContext")
        }

        viewModelScope.launch(Dispatchers.Default + handler) {
            // active
            "Texto".toInt()
            // completing  // cancelling
            // completed   // cancelled
            // Compleción normal  // Compleción excepcional
        }
    }

    fun cancelarYCooperar() {
        viewModelScope.launch(Dispatchers.Default) {
            val job = launch {
                while (isActive) {
                    printWithTag("Estoy viva")
                }

                printWithTag("Se acabó el bucle")
            }

            delay(3000L)
            job.cancel()
        }
    }

    fun seCancelaConDelay() {
        viewModelScope.launch {
            val job = launch(Dispatchers.Default) {
                printWithTag("Activa")

                delay(3000L)

                printWithTag("Completada")
            }

            delay(1000L)
            job.cancel()
            printWithTag("Cancelada")
        }
    }

    fun seCancelaConWithContext() {
        viewModelScope.launch {
            val job = launch(Dispatchers.Default) {
                printWithTag("Activa")
                Thread.sleep(3000L)

                withContext(Dispatchers.Default) {
                    printWithTag("Dentro de withContext")
                }

                printWithTag("Completada")
            }

            delay(1000L)
            job.cancel()
            printWithTag("Cancelada")
        }
    }

    fun seCancelaTrasCoroutineScope() {
        viewModelScope.launch {
            val job = launch(Dispatchers.Default) {
                printWithTag("Activa")
                Thread.sleep(3000L)

                coroutineScope {
                    printWithTag("Dentro de coroutineScope")
                }

                printWithTag("Completada")
            }

            delay(1000L)
            job.cancel()
            printWithTag("Cancelada")
        }
    }

    fun noSeCancelaPorLanzarOtraCorrutinaPeroNoSeLanzan() {
        viewModelScope.launch(Dispatchers.Default) {
            val job = launch {
                printWithTag("Activa")
                Thread.sleep(3000L)

                launch { printWithTag("Me imprimo en la corrutina") }

                printWithTag("Completada")
            }

            delay(1000L)
            job.cancel()
            printWithTag("Cancelada")
        }
    }

    fun noSeCancelaPorSuspension() {
        viewModelScope.launch(Dispatchers.Default) {
            val job = launch {
                printWithTag("Activa")
                Thread.sleep(3000L)

                suspensionVacia()
                printWithTag("Completada")
            }

            delay(1000L)
            job.cancel()
            printWithTag("Cancelada")
        }
    }

    suspend fun suspensionVacia() {
        otraVacia()
    }

    suspend fun otraVacia() {
        printWithTag("Me imprimo en una falsa función de suspensión")
    }

    fun nuncaCancelarScopesGestionados() {
        viewModelScope.cancel()
        viewModelScope.launch {
            printWithTag("Esto no sale")
        }
    }

    fun cancelarJobsOScopesInternos() {
        viewModelScope.launch {
            printWithTag("Entra 1")
            cancel()
            ensureActive()
            printWithTag("Esto no sale")
        }

        viewModelScope.launch {
            printWithTag("Entra 2")
            delay(3000L)
            printWithTag("Pero esto sí")
        }
    }

    fun cancelarScopesPropios() {
        val scope = CoroutineScope(Dispatchers.Default)

        scope.launch {
            printWithTag("A punto de cancelarme")
            delay(3000L)
            printWithTag("Cancelada, ya no me imprimo")
        }

        Thread.sleep(1000L)
        scope.cancel()

        scope.launch {
            printWithTag("Cancelada, yo no me imprimo")
        }
    }

    fun cancelarUnAsync() {
        viewModelScope.launch {

            val ultimaFraseMariano = async(Dispatchers.Default) {
                delay(3000L)
                cancel()
                "MARIANO: A por tabaco, y ahora vuelvo a ti, Encarni."
            }

            launch {
                printAndPost("ENCARNI: ¿Dígame?")
                delay(1000L)
                printAndPost("MARIANO: Soy yo.")
                delay(1000L)
                printAndPost("ENCARNI: ¿\"Yo\" quién?")
                delay(1000L)
                printAndPost("MARIANO: El que se fue.")
                delay(1000L)
                printAndPost("ENCARNI: ¿El que se fue a dónde?")
                delay(1000L)
                printAndPost(ultimaFraseMariano.await())
                delay(1000L)
                printAndPost("ENCARNI: Pero, Mariano, ¡si yo ya me he vuelto a casar!")
            }

            delay(8000L)
            printAndPost("Y se acabó la obra.")
        }
    }

    fun cancelarDesdeUnaFuncionDeSuspension() {
        viewModelScope.launch() {

            launch(Dispatchers.Default) {
                suspenderAlgo()

                if (isActive) {
                    printAndPost("Activa hasta el final")
                }
            }

            launch(Dispatchers.Default) {
                delay(4000L)
                printWithTag("Han transcurrido cuatro segundos")
            }
        }
    }

    private suspend fun suspenderAlgo(): Job = withContext(Dispatchers.Default) {

        cancel()

        if (isActive) {
            printWithTag("Sigo activa")
        } else {
            printWithTag("Pos me han cancelado")
        }


        launch(Dispatchers.Default) {
            delay(2000L)
            printWithTag("No me he muerto")
        }
    }

    fun falloVsCancelacion() {
        val ceh = CoroutineExceptionHandler { context, throwable ->
            printAndPost(throwable.javaClass.name)
        }

        viewModelScope.launch(ceh) {

            val ultimaFraseMariano = async(Dispatchers.Default) {
                delay(3000L)
                throw java.lang.IllegalStateException()
                "MARIANO: A por tabaco, y ahora vuelvo a ti, Encarni."
            }

            launch {
                printAndPost("ENCARNI: ¿Dígame?")
                delay(1000L)
                printAndPost("MARIANO: Soy yo.")
                delay(1000L)
                printAndPost("ENCARNI: ¿\"Yo\" quién?")
                delay(1000L)
                printAndPost("MARIANO: El que se fue.")
                delay(1000L)
                printAndPost("ENCARNI: ¿El que se fue a dónde?")
                delay(1000L)
//                printAndPost(ultimaFraseMariano.await())
                delay(1000L)
                printAndPost("ENCARNI: Pero, Mariano, ¡si yo ya me he vuelto a casar!")
            }

            delay(8000L)
            printAndPost("Y se acabó la obra.")
        }
    }

    val ceh = CoroutineExceptionHandler { context, throwable -> printWithTag(throwable.javaClass.name)}
    val miScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + ceh)

    fun serSupervisor() {
        miScope.launch {

            launch {
                printWithTag("Me voy a morir")
                delay(1000L)
                throw java.lang.Exception()
                printWithTag("Esto no va a salir")
            }

            launch {
                printWithTag("Me lanzo")
                delay(3000L)
                printWithTag("Termino")
            }

            Thread.sleep(2000L)
            printWithTag("Esto no debería salir, pero sale")
        }

        miScope.launch {
            delay(4000L)
            printWithTag("Esto último no sale")
        }
    }

    fun otraFormaDeSupervisar() {
        viewModelScope.launch(Dispatchers.Default + ceh) {
            hacerCosasSuspendidas()
            launch {
                printWithTag("No saldría de estar cancelada")
            }
        }
    }

    private suspend fun hacerCosasSuspendidas() {
        supervisorScope {
            launch { throw java.lang.Exception() }
            delay(2000L)
            launch { printWithTag("Yo creo que no voy ni a nacer") }
        }
    }

    private fun llamaAUnWS() = 3

    private fun llamaAOtroWS() = 4

    private fun llamaAOtroWSMas() = 5

    fun esperarCompletado() {
        viewModelScope.launch {
            printWithTag("Lanzado")
            val res = suspensiones()
            printWithTag("Obtenido")
            res.awaitAll()
        }
    }

    suspend fun suspensiones() = coroutineScope {
        val res1 = async(Dispatchers.Default) {
            printThreadName("1")
            delay(3000L)
            printThreadName("1.2")
            1
        }
        val res2 = async(Dispatchers.Default) {
            printThreadName("2")
            delay(3000L)
            printThreadName("2.2")
            1
        }

        listOf(res1, res2)
    }

    @ExperimentalStdlibApi
    fun comprobarDispatchers(coroutineScope: CoroutineScope) {
        GlobalScope.launch {
            printWithTag("Global scope: ${coroutineContext[CoroutineDispatcher]}")
        }

        viewModelScope.launch {
            printWithTag("View model scope: ${coroutineContext[CoroutineDispatcher]}")
            withContext(Dispatchers.IO) {
                printWithTag("withContext: ${coroutineContext[CoroutineDispatcher]}")
            }
            Job()
        }

        coroutineScope.launch {
            printWithTag("Lifecycle scope: ${coroutineContext[CoroutineDispatcher]}")
        }
    }

    fun laIt() {
        viewModelScope.launch(Dispatchers.Default) {
            val cuenta = async {
                printWithTag("Lanzado")
                Thread.sleep(4000L)
                2 + 3
            }
            cuenta.invokeOnCompletion { printWithTag("Se completó el hijo") }
            delay(1500L)

            cancel()
            printWithTag("Llamando a una función de suspensión")
            printWithTag("${cuenta.await()}")
            printWithTag("Función de suspensión finalizada")
        }.invokeOnCompletion { printWithTag("Se completó el padre") }
    }
}