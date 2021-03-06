package es.resultados.fft;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;


import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import ca.uol.aig.fftpack.RealDoubleFFT;





public class FFTSpectralAnalyzer extends Activity implements OnClickListener {



	//Ojeto de tipo WakeLock que permite mantener despierta la aplicacion
	protected PowerManager.WakeLock wakelock;


	RecordAudio recordTask; // proceso de grabacion y analisis
	AudioRecord audioRecord; // objeto de la clase AudioReord que permite captar el sonido

	Button startStopButton; // boton de arranquue y pausa
	boolean started = false; // condicion del boton



	// Configuracion del canal de audio
	int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
	int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
	static double RATE = 44100; // frecuencia o tasa de muestreo


	int bufferSize = 0;  // tamaño del buffer segun la configuracion de audio
	int bufferReadResult = 0; // tamaño de la lectura
	int blockSize_buffer = 1024; // valor por defecto para el bloque de lectura de buffer



	// Objeto de la clase que determina la FFT de un vector de muestras
	private RealDoubleFFT transformer;
	int blockSize_fft = 2048;// tamaño de la transformada de Fourier
	int escala_fft = (int) ((RATE / 2) / blockSize_fft); //diferencia entre puntos consecutivos de la fft en frecuencia Hz.A cuanto esta un punto de la fft del otro.

	// Frecuencias del rango de estudio asociadas al instrumento
	static double MIN_FREQUENCY = 30; // HZ
	static double MAX_FREQUENCY = 20000; // HZ


	// Valores pordefecto para el estudio de los armonicos

	double UMBRAL = 100; // umbral de amplitud valido para tener en cuenta
						 // los armonicos, depende del tamaño de la FFT

	int LONGTRAMA = 20; // tamaño de la ventana de estudio de los armonicos
						// tambien depende del tamaño de la FFT

	int NUM_ARMONICOS = 6; // numero de armonicos a tener en cuenta



	double[] aux3;{ // declaracion de vector auxiliar para el estudio de la trama
	aux3 = new double[LONGTRAMA];} // sera el array que contenga la amplitud de los armonicos

	double [] validos = new double[NUM_ARMONICOS] ; // vector que tendra solo los armonicos de interes
    int aux_promedio=0; // lo uso para contar cunatas veces promedio la fft


	private static double[] datos1;



	// Elementos para la representacion en pantalla

	int TAM_TEXT = 40;


	TextView statusText; // objeto de la clase TextView para mostrar mensaje

	ImageView imageView; // imagen para la representacion del espectro
	Bitmap bitmap;
	Canvas canvas;
	Paint paint;

	ImageView imageView2; // imagen para dibujar las bandas de frecuencia
	Bitmap bitmap2;
	Canvas canvas2;
	Paint paint2;

	Canvas canvas3;// para dibujar el valor de la SNR
	Paint paint3;

	Canvas canvas4; // para dibujar texto (frecuencia) en el espectrograma
	Paint paint4;

	Canvas canvas5; // para dibujar el promedio de la magnitud de los armonicos en el espectrograma
	Paint paint5;

	Canvas canvas6; // para dibujar el umbral establecido por el usuario
	Paint paint6;


	/// PREFERENCIAS


	// Usamos la clase DecimalFormat para establecer el numero de decimales del resultado
	DecimalFormat df1;
	DecimalFormatSymbols symbols = new  DecimalFormatSymbols(Locale.US);{
    symbols.setDecimalSeparator('.');
    df1= new DecimalFormat("#.#",symbols);}

    // Cuando la actividad es llamada por primera vez
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.graficas);

        // Inicializacion de todos los elementos graficos
        statusText = (TextView) this.findViewById(R.id.StatusTextView);
        startStopButton = (Button) this.findViewById(R.id.StartStopButton);
		startStopButton.setOnClickListener(this);

		int[] escalaPantalla; // eje X , Y , relacion de dimensiones.
		escalaPantalla = screenDimension();

		// Tamaños de texto para los diferentes mensajes y resultados
		int TAM_TEXT1 = 10*escalaPantalla[2];



		 // imagen para la representacion del espectro
		imageView = (ImageView) this.findViewById(R.id.ImageView01);
		bitmap = Bitmap.createBitmap(escalaPantalla[0], escalaPantalla[1],
				Bitmap.Config.ARGB_8888);
		canvas = new Canvas(bitmap);
		paint = new Paint();
		paint.setColor(Color.GREEN);
		imageView.setImageBitmap(bitmap);

		// imagen para dibujar las bandas de frecuencia
		imageView2 = (ImageView) this.findViewById(R.id.ImageView02);
		bitmap2 = Bitmap.createBitmap((int) escalaPantalla[0], TAM_TEXT1,
				Bitmap.Config.ARGB_8888);
		canvas2 = new Canvas(bitmap2);
		paint2 = new Paint();
		paint2.setColor(Color.WHITE);
		imageView2.setImageBitmap(bitmap2);

		// para dibujar el valor de la SNR
		canvas3 = new Canvas(bitmap);
		paint3 = new Paint();
		paint3.setColor(Color.MAGENTA);

		// para dibujar texto (frecuencia) en el espectrograma
		canvas4 = new Canvas(bitmap);
		paint4 = new Paint();
		paint4.setColor(Color.YELLOW);

		// para dibujar el promedio de la magnitud de los armonicos en el espectrograma
		canvas5 = new Canvas(bitmap);
		paint5 = new Paint();
		paint5.setColor(Color.RED);

		 // para dibujar el umbral establecido por el usuario
		canvas6 = new Canvas(bitmap);
		paint6 = new Paint();
		paint6.setColor(Color.DKGRAY);

		//evitar que la pantalla se apague
        final PowerManager pm=(PowerManager)getSystemService(Context.POWER_SERVICE);
        this.wakelock=pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "etiqueta");
        wakelock.acquire();

        // Dibuja el eje de frecuencias
        DibujaEjeFrecuencias();

    }

    ////////////////////////////////////////////////////////////////////////
	// Hace que la pantalla siga encendida hasta que la actividad termine
	protected void onDestroy(){
	        super.onDestroy();

	        this.wakelock.release();

	 }

	// Adicionalmente, se recomienda usar onResume, y onSaveInstanceState, para que,
	// si minimizamos la aplicacion, la pantalla se apague normalmente, de lo
	// contrario, no se apagará la pantalla aunque no tengamos a nuestra aplicación
	// en primer plano.

	protected void onResume(){
	        super.onResume();
	        wakelock.acquire();
	        // Valor que muestra el boton al volver a la actividad
	        startStopButton.setText("ON");
	 }

	// Si se sale de la actividad de manera inesperada
    @Override
    protected void onPause() {
        super.onPause();
        if(started) {

        	started = false;
        }
    }

    public void onSaveInstanceState(Bundle icicle) {
        super.onSaveInstanceState(icicle);
        this.wakelock.release();

    }


    // PROCESO O TAREA ASINCRONA QUE SENCARGARA DE RECOGER Y ANALIZAR LA SEÑAL DE AUDIO DE ENTRADA
    private class RecordAudio extends AsyncTask<Void, short[], Void> {

		@Override
		protected Void doInBackground(Void... params) {
			try {



				// estimacion del tamaño del buffer en funcion de la configuracion de audio
				bufferSize = AudioRecord.getMinBufferSize((int)RATE,
						channelConfiguration, audioEncoding);

				// inicializacion del objeto de la clase AudioRecord
				AudioRecord audioRecord = new AudioRecord(
						MediaRecorder.AudioSource.MIC,(int) RATE,
						channelConfiguration, audioEncoding, bufferSize);

				// declaracion del vector que almacenara primero los datos recogidos del microfono
				short[] audio_data = new short[blockSize_buffer]; // tipo de dato short (2^15 = 32768)

				audioRecord.startRecording(); // empieza a grabar

				while (started) { // mientras no se pulse de nuevo el boton

					// tamañod de la lectura
					bufferReadResult = audioRecord.read(audio_data, 0, blockSize_buffer);


					// se mandan las muestras recogidas para su procesado
					publishProgress(audio_data);

				}

				audioRecord.stop(); // para la grabacion momentaneamente

			} catch (Throwable t) { // en caso de error, p.ej. captura de audio ya activa
				Log.e("AudioRecord", "Recording Failed");
			}

			return null;
		}

		protected void onProgressUpdate(short[]... toTransform) {


			//pasa valores de pantalla para no tener que entrar de nuevo a cada funcion

			int[] escalaPantalla; // eje X , Y , relacion de dimensiones.
			escalaPantalla = screenDimension();


			// Arrays con las muestras de audio en tiempo y frecuencia en formato double
			double[] trama, trama_espectro, trama_oct;
			trama = new double[blockSize_fft];

			// inicializamos el vector que contendra la FFT de
			transformer = new RealDoubleFFT(blockSize_fft);


			for (int i = 0; i < bufferReadResult; i++) {

				trama[i] = (double) toTransform[0][i];
				//trama[i * 2 + 1] = 0; // aumentaremos la resolucion en frecuencia de la transformada interpolando ceros

			}


			// normalizamos la trama de sonido dividiendo todas las muestra por la de mayor valor
			//normaliza(trama);


			// Conseguimos precision con el enventanado
			// Filtra los armonicos en el espectro
			// Destaca y realza los fundamentales

			trama = aplicaHamming(trama);

			// Dominio transformado. Realiza la FFT de la trama
			transformer.ft(trama);

			statusText.setTextSize(TAM_TEXT); // definimos el tamaño para el texto

			DibujaEjeFrecuencias(); // Dibuja las bandas que componen el eje de frecuencias

			// Normalizamos el espectro para su representacion
			//trama_espectro = normaliza(trama);
			//trama_espectro=logRep(trama);
            trama=promedioFFT(trama,aux_promedio);
           // trama_oct = FFT_octavas(trama);
           // DibujaEspectro(trama, trama_oct ,escalaPantalla);

			//promedio la fft la cantidad de veces que figura en el if, y solo lo muestra despues de promediar.
			aux_promedio= aux_promedio +1;
			if (aux_promedio==10) {
                DibujaEspectro(trama, escalaPantalla); // representa graficamente el espectro de la señal
                aux_promedio=0;
            }



		}
	}




    ///////////////////////////////////////////////////////////////////////////////
	//DIBUJA EL EJE DE FRECUENCIAS/////////////////////////////////////////////////
    public void DibujaEjeFrecuencias(){

		int[] escalaPantalla=screenDimension();
		canvas2.drawColor(Color.BLACK);
		paint2.setAntiAlias(true);
		paint2.setFilterBitmap(true);

		// Valores que se mostrara en el eje X
		int[]bandas ={30,60,125,250,500,1000,2000,4000,8000,16000};
		paint2.setStrokeWidth(5);
		canvas2.drawLine(0,0,escalaPantalla[0],0,paint2);
		int factor1=escalaPantalla[0]/3; //ancho x de la pantalla divido 3. Queda el eje del ancho de la pantalla completo.
		int TAM_TEXT3 = 7*escalaPantalla[2];
		int TAM_TEXT1 = 10*escalaPantalla[2];


		paint2.setTextSize(TAM_TEXT3);
        // Grafica el eje X en funciona de la frecuenca logaritmicamente. De esta forma queda por octavas.
		for(int i=0; i < bandas.length;i++){
            canvas2.drawText(String.valueOf(bandas[i]),Math.round(factor1*Math.log10(bandas[i]))-(TAM_TEXT3)/2-Math.round(factor1*Math.log10(bandas[0]))+30,TAM_TEXT3,paint2);
			}
		canvas2.drawText("Hz",escalaPantalla[0] - TAM_TEXT1,TAM_TEXT3,paint2);

		imageView2.invalidate();


    }


    ///////////////////////////////////////////////////////////////////////////////
	//DIBUJA EL ESPECTRO///////////////////////////////////////////////////////////
    public void DibujaEspectro(double[] trama_espectro, int[] escala){

    	// Claculo del la relacion Señal a Ruido (dB)
		// Resulta del cociente entre el valor maximo del espectro entre el pormedio
		// Lo ideal es que la SNR valga infinio, lo que significa que no hay ruido
		//double snr2 = 10*Math.log10(max(trama_espectro,0,trama_espectro.length).valor/promedio(trama_espectro));
		int factor1=escala[0]/3;
		int TAM_TEXT3 = 7*escala[2];
		int x1;
		int x2;
		int downy ;
		int upy;
		canvas.drawColor(Color.BLACK);
		int[]bandas ={30,60,125,250,500,1000,2000,4000,8000,16000};
		int[] bandas_tercios={25,31,42,52,63,79,101,128,160,203,251,321,402,510,639,805,1015,1279,1612,2032,2559,3226,4066,5120,6449,8128,10242,12900,16257};

		for(int i=0; i < bandas.length;i++){ // muestra las lineas verticales por octavas
			canvas.drawLine(Math.round(factor1*Math.log10(bandas[i]))-(TAM_TEXT3)/2-Math.round(factor1*Math.log10(bandas[0]))+50,0,Math.round(factor1*Math.log10(bandas[i]))-(TAM_TEXT3)/2-Math.round(factor1*Math.log10(bandas[0]))+50,escala[1],paint6);
		}
		for(int i=0; i < escala[1]+400/100;i++){ //muestra las lineas horizontales en db
			canvas.drawLine(0,i*50,escala[0],i*50,paint6);
		}
/*
		for (int i = 0; i < trama_oct.length; i++) {
			x1 = (int) (Math.round(factor1*Math.log10(i*escala_fft))-(TAM_TEXT3)/2-Math.round(factor1*Math.log10(MIN_FREQUENCY))+80);
			x2 = (int) (Math.round(factor1*Math.log10(i*escala_fft))-(TAM_TEXT3)/2-Math.round(factor1*Math.log10(MIN_FREQUENCY))+80);
			downy = (int) (400+escala[1]-(100*Math.log10( Math.abs(trama_oct[i])*escala[1])));
			upy =  escala[1];
			canvas5.drawRect(Math.round(factor1*Math.log10(bandas_tercios[i]))-(TAM_TEXT3)/2-Math.round(factor1*Math.log10(bandas_tercios[0]))+50,downy,Math.round(factor1*Math.log10(bandas_tercios[i+1]))-(TAM_TEXT3)/2-Math.round(factor1*Math.log10(bandas_tercios[0]))+50,upy, paint5);
		}

*/


		for (int i = 0; i < trama_espectro.length-1; i++) {

			/*
			int x = (int) (Math.round(factor1*Math.log10(i*escala_fft))-(TAM_TEXT3)/2-Math.round(factor1*Math.log10(MIN_FREQUENCY))+80);
			downy = (int) (escala[1]-(50*Math.log10( Math.abs(trama_espectro[i])*escala[1])));
			upy =  escala[1] ;
			canvas.drawLine(x, downy, x, upy, paint);
*/

			//////////////////FFT con lineas ///////////////////////
		    // avanza en X logaritmicamente para que coincida con la representacion por octavas.
            //El espectro se divide en intervalos regulares por la FFT por lo que en baja frecuencia hay menos resolucion.
            x1 = (int) (Math.round(factor1*Math.log10(i*escala_fft))-(TAM_TEXT3)/2-Math.round(factor1*Math.log10(MIN_FREQUENCY))+80);
			 x2= (int) (Math.round(factor1*Math.log10((i+1)*escala_fft))-(TAM_TEXT3)/2-Math.round(factor1*Math.log10(MIN_FREQUENCY))+80);
			 downy = (int) (400+escala[1]-(100*Math.log10( Math.abs(trama_espectro[i])*escala[1])));
			 upy =  (int) (400+escala[1]-(100*Math.log10( Math.abs(trama_espectro[i+1])*escala[1])));
			 canvas.drawLine(x1, downy, x2, upy, paint);


			 // Completo los valores faltantes de muestras de la FFT en el eje X.
/*			for (int j=x; j < (Math.round(factor1*Math.log10((i+1)*escala_fft))-(TAM_TEXT3)/2-Math.round(factor1*Math.log10(MIN_FREQUENCY))+80);j++){
			    downy = (int) (escala[1] - (trama_espectro[i]*escala[1]));
				upy = escala[1];
				canvas.drawLine(j, downy, j, upy, paint);
			}*/
		}


		imageView.invalidate();

    }




	// Metodo para el calculo del promedio de un vector de muestras.

    private static double promedio(double[] datos) {

        int N = datos.length;
        double med = 0;
        for (int k = 0; k < N; k++) {

            med += Math.abs(datos[k]);
        }
        med = med / N;
        return med;
    }

    // Metodo para el calculo de la media de un vector de muestras.

    private static double media(double[] datos) {
        // Computo de la media.
        int N = datos.length;
        double med = 0;
        for (int k = 0; k < N; k++) {

            med += datos[k];
        }
        med = med / N;
        return med;
    }

    // Metodo para el calculo de la varianza de un vector de muestras.

  /*  private static double varianza(double[] datos) {
        // Computo de la media.
        int N = datos.length;
        double med = media(datos);
        // Computo de la varianza.
        double varianza = 0;
        for (int k = 0; k < N; k++) {
            varianza += Math.pow(datos[k] - med, 2);
        }
        varianza = varianza / (N - 1);
        return varianza;
    }*/


	// Metodo para la normalizacion de un vector de muestras.

    private static double[] normaliza(double[] datos) {

    	double maximo = 0;
        for (int k = 0; k < datos.length; k++) {
            if (Math.abs(datos[k]) > maximo) {
                maximo = Math.abs(datos[k]);
            }
        }
        for (int k = 0; k < datos.length; k++) {
            datos[k] = datos[k] / maximo;
        }
        return datos;
    }


    //represento logaritmicamente los valores
    private static double[] logRep (double[] datos){
    	for (int i=0; i<datos.length;i++){
    		datos[i]=20*Math.log10(Math.abs(datos[i]/1000000));
		}
    	return datos;

	}

    // Metodo para enventanar Hamming un vector de muestras.

    private static double[] aplicaHamming(double[] datos) {
        double A0 = 0.53836;
        double A1 = 0.46164;
        int Nbf = datos.length;
        for (int k = 0; k < Nbf; k++) {
            datos[k] = datos[k] * (A0 - A1 * Math.cos(2 * Math.PI * k / (Nbf - 1)));
        }
        return datos;
    }


    // Hace el promedio de varias FFT, para mostrar que no varie tan rapido y muestre
    private static double[] promedioFFT (double [] datos,int aux_promedio){
		if (aux_promedio==0)
			datos1=datos;
    	for( int i=0; i<datos.length;i++) {
			datos1[i] = (Math.abs(datos1[i]) + Math.abs(datos[i]))/2;
		}
		return datos1;
	}


	/*

    // Función que devuelve un objeto de la clase Maximo,que contiene:
	// valor máximo y posicion en la trama que se pasa como parametro.
	// Entradas:
	// - x = trama o array a analizar
	// - ini = comienzo de la trama
	// - fin = fin de la trama
	// Salida:
	// - Maximo: objeto de la clase Maximo que contiene (valor, posicion)
	// del maximo de la trama
	public Maximo max(double[] x, int ini, int fin) {

		Maximo miMaximo;
		miMaximo = new Maximo();

		for (int i = ini; i < fin; i++) {
			if (Math.abs(x[i]) >= miMaximo.valor) {
				miMaximo.valor = Math.abs(x[i]);
				miMaximo.pos = i;
			}

		}

		return miMaximo;

	}

	// Definicion de la clase del objeto Maximo
	class Maximo {
		int pos;// posicion
		double valor;
	}*/


	public void onClick(View v) {
		if (started) {

			started = false;
			startStopButton.setText("ON");
            recordTask.cancel(true);
            validos[1]=0;



        } else {
			started = true;
			startStopButton.setText("OFF");
			recordTask = new RecordAudio();
			recordTask.execute();

		}
	}

	private double [] FFT_octavas(double[] datos){
		double datos_oct[]=new double[30];
		for (int i=1; i<30;i++){
			int oct = (int) Math.round( blockSize_fft/(Math.pow(2,30-i)));
			datos_oct[i]=datos[i];

			for(int j=oct;j<Math.round((20*Math.pow(2,oct/3)-20)/escala_fft);j++) {
				datos_oct[i] = (double) Math.round( Math.sqrt( Math.pow(datos_oct[i],2) + Math.pow(datos[j],2)));
				//datos_oct[i] = 10 * Math.log10(Math.pow(10, datos_oct[i] / 10) + Math.pow(10, datos[j] / 10));
			}
		}
	return datos_oct;
	}

 //funcion que toma las medidas de la pantalla y la relacion de dimensiones entre el alto y el ancho para manejar el tamaño del texto
	public int [] screenDimension (){
		int [] pantalla = new int[3];
		Display display = getWindowManager().getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);
		pantalla[0] = (int) Math.round((double)size.x); // [0] eje X (ancho)
		pantalla[1] = (int) Math.round((double)size.y *0.6) ; // [1] eje y (alto)
		pantalla[2] = (int) Math.round((double)pantalla[0]/(double)pantalla[1]); //adptativo: [2] relacion de dimensiones.
		return pantalla;
	}

}