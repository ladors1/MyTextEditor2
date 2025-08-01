package com.example.mytexteditor

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.*
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.ColorPickerDialog
import java.io.IOException

class MainActivity : AppCompatActivity() {

    // UI
    private lateinit var etText: EditText
    private lateinit var spinnerFont: Spinner
    private lateinit var btnTextColor: Button
    private lateinit var btnBgColor: Button
    private lateinit var btnTransparentBg: Button
    private lateinit var imagePreview: ImageView
    private lateinit var btnSaveImage: Button
    private lateinit var seekFontSize: SeekBar
    private lateinit var tvFontSize: TextView

    // وضعیت
    private var bgColor = Color.WHITE
    private var isTransparentBg = false
    private var fontSizeInSp = 26f
    private var currentFontPath = ""
    private var fontList: Map<String, String> = mapOf()
    private var isProgrammaticTextChange = false
    private var activeColor = Color.BLACK // رنگ فعال برای کل متن

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupFonts()
        setupListeners()

        renderTextImage()
    }

    private fun bindViews() {
        etText = findViewById(R.id.etText)
        spinnerFont = findViewById(R.id.spinnerFont)
        btnTextColor = findViewById(R.id.btnTextColor)
        btnBgColor = findViewById(R.id.btnBgColor)
        btnTransparentBg = findViewById(R.id.btnTransparentBg)
        imagePreview = findViewById(R.id.imagePreview)
        btnSaveImage = findViewById(R.id.btnSaveImage)
        seekFontSize = findViewById(R.id.seekFontSize)
        tvFontSize = findViewById(R.id.tvFontSize)
    }

    private fun setupFonts() {
        fontList = getFontList(this)
        if (fontList.isEmpty()) {
            Toast.makeText(this, "هیچ فونت سالمی پیدا نشد!", Toast.LENGTH_LONG).show()
            finish(); return
        }
        val fontNames = fontList.keys.toList()
        currentFontPath = fontList[fontNames[0]].orEmpty()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fontNames)
        spinnerFont.adapter = adapter
    }

    private fun setupListeners() {
        spinnerFont.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                currentFontPath = fontList[parent.getItemAtPosition(pos) as String].orEmpty()
                renderTextImage()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        etText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { if (!isProgrammaticTextChange) renderTextImage() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        seekFontSize.progress = fontSizeInSp.toInt()
        tvFontSize.text = "اندازه فونت: ${fontSizeInSp.toInt()}"
        seekFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                fontSizeInSp = if (p > 0) p.toFloat() else 1f
                tvFontSize.text = "اندازه فونت: $p"; renderTextImage()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        btnTextColor.setOnClickListener { openColorPicker(true) }
        btnBgColor.setOnClickListener { openColorPicker(false) }
        btnTransparentBg.setOnClickListener { isTransparentBg = !isTransparentBg; renderTextImage() }
        btnSaveImage.setOnClickListener { saveImage() }
    }

    private fun openColorPicker(isText: Boolean) {
        ColorPickerDialog.Builder(this)
            .setTitle(if (isText) "انتخاب رنگ متن" else "انتخاب رنگ پس‌زمینه")
            .setPositiveButton("تأیید",
                ColorEnvelopeListener { envelope, _ ->
                    val opaqueColor = (envelope.color and 0x00FFFFFF) or (0xFF000000).toInt()
                    if (isText) {
                        activeColor = opaqueColor
                        renderTextImage()
                    } else {
                        bgColor = opaqueColor
                        renderTextImage()
                    }
                })
            .setNegativeButton("انصراف") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    // *** روش جدید: دیگر از Spannable استفاده نمی‌کنیم، چون موتور رندر با آن مشکل دارد ***
    // ما فقط یک رنگ اصلی (activeColor) را نگه می‌داریم و متن را با آن رنگ می‌کنیم.
    private fun renderTextImage() {
        val textToRender = etText.text.toString()
        val width = 1080
        val height = 1920
        val safeZone = 80f

        val finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val finalCanvas = Canvas(finalBitmap)

        if (isTransparentBg) {
            finalCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        } else {
            finalCanvas.drawColor(bgColor)
        }

        if (textToRender.isEmpty()) {
            imagePreview.setImageBitmap(finalBitmap)
            return
        }

        // 1. یک بوم موقت فقط برای کشیدن ماسک سیاه متن ایجاد می‌کنیم
        val textMaskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val textCanvas = Canvas(textMaskBitmap)

        // 2. متن را با رنگ سیاه خالص روی این بوم موقت می‌کشیم
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSizeInSp, resources.displayMetrics)
            textAlign = Paint.Align.CENTER
            try {
                typeface = Typeface.createFromAsset(assets, currentFontPath)
            } catch (e: Exception) {}
        }

        // رسم خط به خط برای پشتیبانی از متن چندخطی
        val textLines = textToRender.split('\n')
        val textHeight = textPaint.descent() - textPaint.ascent()
        val totalTextHeight = textHeight * textLines.size
        var yPos = (height - totalTextHeight) / 2f - textPaint.ascent()

        for (line in textLines) {
            textCanvas.drawText(line, width / 2f, yPos, textPaint)
            yPos += textHeight
        }

        // 3. یک قلم‌مو (Paint) با رنگ دلخواه خود می‌سازیم
        val colorPaint = Paint().apply {
            color = activeColor
            // 4. حالت ترکیب را روی SRC_IN تنظیم می‌کنیم
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }

        // 5. روی بوم نهایی، ابتدا ماسک سیاه را می‌کشیم، سپس آن را با رنگ خود پر می‌کنیم
        finalCanvas.drawBitmap(textMaskBitmap, 0f, 0f, colorPaint)

        imagePreview.setImageBitmap(finalBitmap)
    }


    private fun getFontList(context: Context): Map<String, String> {
        return try {
            context.assets.list("fonts")
                ?.filter { it.endsWith(".ttf", true) || it.endsWith(".otf", true) }
                ?.associateBy({ it.substringBeforeLast('.') }, { "fonts/$it" })
                ?: emptyMap()
        } catch (e: IOException) {
            emptyMap()
        }
    }

    private fun saveImage() {
        val drawable = imagePreview.drawable as? BitmapDrawable ?: return
        val bmp = drawable.bitmap
        val filename = "TextImage_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/MyTextEditor")
            }
        }
        try {
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { it.write(bmp.toByteArray()) }
                Toast.makeText(this, "تصویر با موفقیت ذخیره شد!", Toast.LENGTH_LONG).show()
            }
        } catch (e: IOException) {
            Toast.makeText(this, "خطا در ذخیره تصویر: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun Bitmap.toByteArray(): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
