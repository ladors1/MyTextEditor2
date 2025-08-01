package com.example.mytexteditor

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.provider.MediaStore
import android.text.*
import android.text.style.ForegroundColorSpan
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.ColorPickerDialog

class MainActivity : AppCompatActivity() {

    lateinit var etText: EditText
    lateinit var spinnerFont: Spinner
    lateinit var btnTextColor: Button
    lateinit var btnBgColor: Button
    lateinit var btnTransparentBg: Button
    lateinit var imagePreview: ImageView
    lateinit var btnSaveImage: Button
    lateinit var seekFontSize: SeekBar
    lateinit var tvFontSize: TextView

    var textColor = Color.BLACK
    var bgColor = Color.WHITE
    var isTransparentBg = false
    var fontSize = 26f

    lateinit var fontList: List<String>
    var currentFont = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etText = findViewById(R.id.etText)
        spinnerFont = findViewById(R.id.spinnerFont)
        btnTextColor = findViewById(R.id.btnTextColor)
        btnBgColor = findViewById(R.id.btnBgColor)
        btnTransparentBg = findViewById(R.id.btnTransparentBg)
        imagePreview = findViewById(R.id.imagePreview)
        btnSaveImage = findViewById(R.id.btnSaveImage)
        seekFontSize = findViewById(R.id.seekFontSize)
        tvFontSize = findViewById(R.id.tvFontSize)

        fontList = getFontList(this)
        if (fontList.isEmpty()) {
            Toast.makeText(this, "هیچ فونت سالمی پیدا نشد! فونت مناسب در assets/fonts قرار بده.", Toast.LENGTH_LONG).show()
            finish()
        }
        currentFont = fontList[0]

        val fontNames = fontList.map { it.substringBefore(".") }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fontNames)
        spinnerFont.adapter = adapter

        spinnerFont.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentFont = fontList[position]
                renderTextImage()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        etText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { renderTextImage() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        seekFontSize.progress = fontSize.toInt()
        tvFontSize.text = "اندازه فونت: ${fontSize.toInt()}"

        seekFontSize.setOnSeekBarChangeListener(object: OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                fontSize = progress.toFloat()
                tvFontSize.text = "اندازه فونت: $progress"
                renderTextImage()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnTextColor.setOnClickListener { openColorPicker(true) }
        btnBgColor.setOnClickListener { openColorPicker(false) }
        btnTransparentBg.setOnClickListener {
            isTransparentBg = !isTransparentBg
            renderTextImage()
        }
        btnSaveImage.setOnClickListener { saveImage() }

        renderTextImage()
    }

    fun getFontList(context: Context): List<String> {
        val allFonts = context.assets.list("fonts")?.filter { it.endsWith(".ttf") || it.endsWith(".otf") } ?: listOf()
        val validFonts = mutableListOf<String>()
        for (f in allFonts) {
            try {
                Typeface.createFromAsset(context.assets, "fonts/$f")
                validFonts.add(f)
            } catch (e: Exception) {
                Toast.makeText(this, "مشکل در فونت: $f (${e.message})", Toast.LENGTH_SHORT).show()
            }
        }
        return validFonts
    }

    fun openColorPicker(isText: Boolean) {
        ColorPickerDialog.Builder(this)
            .setTitle(if (isText) "انتخاب رنگ متن" else "انتخاب رنگ پس‌زمینه")
            .setPreferenceName("ColorPickerDialog")
            .setPositiveButton("تأیید",
                ColorEnvelopeListener { envelope, _ ->
                    if (isText) {
                        applyColorToSelection(envelope.color)
                    } else {
                        bgColor = envelope.color
                        renderTextImage()
                    }
                })
            .setNegativeButton("انصراف") { dialogInterface, _ -> dialogInterface.dismiss() }
            .show()
    }

    // حل کامل مشکل رنگ برای متن انتخابی یا کل متن (در ادیت و خروجی)
    fun applyColorToSelection(color: Int) {
        val start = etText.selectionStart
        val end = etText.selectionEnd
        val spannable = etText.text as Spannable

        if (start < end) {
            // حذف spanهای قبلی فقط در بازه انتخاب شده
            val spans = spannable.getSpans(start, end, ForegroundColorSpan::class.java)
            for (span in spans) {
                spannable.removeSpan(span)
            }
            spannable.setSpan(
                ForegroundColorSpan(color),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        } else {
            // اگر انتخاب نداره، کل متن رنگی بشه
            val allSpans = spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java)
            for (span in allSpans) {
                spannable.removeSpan(span)
            }
            etText.setTextColor(color)
            textColor = color
        }
        renderTextImage()
    }

    // حل مشکل برعکس شدن متن فارسی و SafeZone در خروجی عکس
    fun renderTextImage() {
        val width = 1080
        val height = 1920
        val safeZone = 80f // فاصله از پایین و لبه‌ها
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (!isTransparentBg)
            canvas.drawColor(bgColor)
        val paint = Paint()
        paint.textSize = fontSize * 2.5f
        paint.isAntiAlias = true
        paint.textAlign = Paint.Align.RIGHT

        try {
            val tf = Typeface.createFromAsset(assets, "fonts/$currentFont")
            paint.typeface = tf
        } catch (e: Exception) {
            paint.typeface = Typeface.DEFAULT
        }

        val text = etText.text
        val textLines = text.split("\n")
        val yStart = (height - ((textLines.size - 1) * fontSize * 2.5f)) / 2f
        var charOffset = 0
        for ((i, line) in textLines.withIndex()) {
            val x = width - safeZone // همیشه نزدیک راست تصویر، با فاصله مناسب
            var j = 0
            while (j < line.length) {
                var charColor = textColor
                val spans = (text as Spannable).getSpans(charOffset + j, charOffset + j + 1, ForegroundColorSpan::class.java)
                if (spans.isNotEmpty()) {
                    charColor = spans.last().foregroundColor
                }
                paint.color = charColor
                val prefix = line.substring(0, j)
                val charX = x - paint.measureText(line.substring(j))
                canvas.drawText(line[j].toString(), charX, yStart + i * fontSize * 2.5f, paint)
                j++
            }
            charOffset += line.length + 1
        }
        imagePreview.setImageBitmap(bmp)
    }

    fun saveImage() {
        try {
            val bmp = Bitmap.createBitmap(
                imagePreview.width, imagePreview.height, Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bmp)
            imagePreview.draw(canvas)
            MediaStore.Images.Media.insertImage(
                contentResolver, bmp, "TextImage", "متن ذخیره شده"
            )
            Toast.makeText(this, "تصویر ذخیره شد و قابل استفاده برای استوری یا استیکر!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در ذخیره تصویر: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
