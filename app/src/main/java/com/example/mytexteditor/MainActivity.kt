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

    // ✅ تغییر رنگ بخش انتخابی یا کل متن (EditText)
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
            // نیاز به setText نیست. همین کافی است
        } else {
            // اگر انتخاب نداره، کل متن رنگی بشه
            etText.setTextColor(color)
            textColor = color
            // برای یکنواختی، تمام spanهای رنگی پاک شه:
            val allSpans = spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java)
            for (span in allSpans) {
                spannable.removeSpan(span)
            }
            renderTextImage()
        }
        renderTextImage()
    }

    // ✔️ رندر عکس با رعایت رنگ spanها (تک تک کاراکترها)
    fun renderTextImage() {
        val width = 1080
        val height = 1920
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (!isTransparentBg)
            canvas.drawColor(bgColor)
        val paint = Paint()
        paint.textSize = fontSize * 2.5f
        paint.isAntiAlias = true
        paint.textAlign = Paint.Align.LEFT // حالا چپ چین. اگر وسط می‌خواهی، Paint.Align.CENTER بذار و مختصات رو تغییر بده.

        try {
            val tf = Typeface.createFromAsset(assets, "fonts/$currentFont")
            paint.typeface = tf
        } catch (e: Exception) {
            paint.typeface = Typeface.DEFAULT
            Toast.makeText(this, "مشکل در لود فونت (${currentFont}): ${e.message}", Toast.LENGTH_SHORT).show()
        }

        val text = etText.text
        val lines = text.split("\n")
        var y = height / 2f - (lines.size - 1) * fontSize * 1.5f

        var charOffset = 0

        for (line in lines) {
            var x = width / 2f
            // برای center-align:
            paint.textAlign = Paint.Align.CENTER
            x = width / 2f
            // حالا هر کاراکتر رو با span رنگ مناسب چاپ کن
            var i = 0
            while (i < line.length) {
                val c = line[i]
                // رنگ پیشفرض span یا کل
                var charColor = textColor
                val spans = (text as Spannable).getSpans(charOffset + i, charOffset + i + 1, ForegroundColorSpan::class.java)
                if (spans.isNotEmpty()) {
                    charColor = spans[0].foregroundColor
                }
                paint.color = charColor
                // اندازه و موقعیت کاراکتر فعلی
                val charWidth = paint.measureText(c.toString())
                // چون center-align هست، باید کل طول خط رو محاسبه کنی:
                val lineWidth = paint.measureText(line)
                val charX = x - lineWidth / 2f + paint.measureText(line.substring(0, i))
                canvas.drawText(c.toString(), charX, y, paint)
                i++
            }
            y += fontSize * 2.5f
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
