package es.uniovi.converter

import android.R.attr
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL


class MainActivity : AppCompatActivity() {

    private lateinit var mSourceCurrency: String
    private lateinit var mTargetCurrency: String
    var mConversionRate: Double = 1.0

    private var lastSourceCurrency: String? = null
    private var lastTargetCurrency: String? = null

    private lateinit var mSpinnerSource: Spinner
    private lateinit var mSpinnerTarget: Spinner
    lateinit var mEditTextSource: EditText
    lateinit var mEditTextTarget: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        mSpinnerSource = findViewById(R.id.spinnerSource)
        mSpinnerTarget = findViewById(R.id.spinnerTarget)
        mEditTextSource = findViewById(R.id.editTextSource)
        mEditTextTarget = findViewById(R.id.editTextTarget)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    fun onClickToConvert (view: View){
        val sourceCurrencySelected = mSpinnerSource.selectedItem != null && mSpinnerSource.selectedItem != "Choose a currency"
        val targetCurrencySelected = mSpinnerTarget.selectedItem != null && mSpinnerTarget.selectedItem != "Choose a currency"

        // Validamos que se hayan seleccionado las monedas
        if (!sourceCurrencySelected || !targetCurrencySelected) {
            Toast.makeText(this, "Please select both currencies", Toast.LENGTH_SHORT).show()
            return
        }

        mSourceCurrency = mSpinnerSource.selectedItem.toString()
        mTargetCurrency = mSpinnerTarget.selectedItem.toString()

        if (mSourceCurrency == lastSourceCurrency && mTargetCurrency == lastTargetCurrency) {
            // Las monedas no han cambiado, usar ratio guardado
            convert(mEditTextSource, mEditTextTarget, mConversionRate)
            Toast.makeText(this, "Conversion done (without new API call)", Toast.LENGTH_SHORT).show()
        } else {
            // Se han cambiado las monedas, guardar y llamar a la API para obtener nuevo ratio
            lastSourceCurrency = mSourceCurrency
            lastTargetCurrency = mTargetCurrency

            val url = "https://apilayer.net/api/live?access_key=01b0d505abf15c6aa6c7cb43d6be0b61&currencies=$mTargetCurrency&source=$mSourceCurrency&format=1"
            Log.d("CURRENCY_URL", url)
            UpdateRateTask(this@MainActivity, mSourceCurrency, mTargetCurrency).execute(url)
        }
    }

    fun convert (editTextSource: EditText, editTextDestination: EditText, ConversionFactor: Double){
        val StringSource: String = editTextSource.text.toString()

        val NumberSource: Double
        try {
            NumberSource = StringSource.toDouble()
        } catch (nfe: NumberFormatException) {
            return
        }
        val NumberDestination = NumberSource * ConversionFactor

        val StringDestination = NumberDestination.toString()

        editTextDestination.setText(StringDestination)
    }
}

class UpdateRateTask(private val activity: MainActivity, private val sourceCurrency: String, private val targetCurrency: String
) : AsyncTask<String, Void, String?>() {

    override fun doInBackground(vararg urls: String): String? {
        try {
            return getCurrencyRateUsdRate(urls[0])
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    fun getCurrencyRateUsdRate(urlString: String): String {
        return readStream(openUrl(urlString))
    }

    override fun onPostExecute(result: String?) {
        if (result != null) {
            activity.mConversionRate = parseDataFromNetwork(result, sourceCurrency, targetCurrency)
            activity.convert(activity.mEditTextSource, activity.mEditTextTarget, activity.mConversionRate)
            Toast.makeText(activity, "Conversion done", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(activity, "An error occured trying to get conversion rate", Toast.LENGTH_SHORT).show()
        }
    }
}

@Throws(IOException::class)
fun openUrl(urlString: String): InputStream {
    val url = URL(urlString)
    val conn = url.openConnection() as HttpURLConnection
    conn.readTimeout = 10000
    conn.connectTimeout = 15000
    conn.requestMethod = "GET"
    conn.doInput = true

    // Starts the query
    conn.connect()
    return conn.inputStream
}

@Throws(IOException::class)
fun readStream(urlStream: InputStream?): String {
    val r = BufferedReader(InputStreamReader(urlStream))
    val total = StringBuilder()
    var line: String?
    while ((r.readLine().also { line = it }) != null) {
        total.append(line)
    }
    return total.toString()
}

@Throws(JSONException::class)
fun parseDataFromNetwork(data: String, source: String, target: String): Double {
    Log.d("API_RESPONSE", data) // depuracion

    val key = source + target // ejemplo: "USDEUR"
    Log.d("KEY_RATIO", key) // depuracion
    val `object`: JSONObject = JSONObject(data)
    val quotes = `object`.get("quotes")

    when (quotes) {
        is JSONObject -> { // Caso esperado: un JSONObject
            val ratio = quotes.getDouble(key)
            return ratio
        }
        is JSONArray -> { // Caso inesperado: JSONArray (por ejemplo cuado se envia como source y target la misma moneda)
            if (quotes.length() == 0 && source == target) {
                return 1.0 // Si las monedas son iguales, la tasa es 1.0
            } else {
                throw JSONException("Invalid quotes format: expected JSONObject or empty JSONArray for same currency.")
            }
        }
        else -> throw JSONException("Invalid type for quotes") // Cualqueir otro caso inesperado
    }

}