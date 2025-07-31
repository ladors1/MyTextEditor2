package com.example.mytexteditor

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.provider.MediaStore
import android.text.*
import android.text.style.ForegroundColorSpan
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import yuku.ambilwarna.AmbilWarnaDialog   // <-- فقط همین ایمپورت جدید!

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

        try {
            etText = findViewById(R.id.etText)
            spinnerFont = findViewById(R.id.spinnerFont)
            btnTextColor = findViewById(R.id.btnTextColor)
            btnBgColor = findViewById(R.id.btnBgColor)
            btnTransparentBg = findViewById(R.id.btnTransparentBg)
            imagePreview = findViewById(R.id.imagePreview)
            btnSaveImage = findViewById(R.id.btnSaveImage)
            seekFontSize = findViewById(R.id.seekFontSize)
            tvFontSize = findViewById(R.id.tvFontSize)
        } catch (e: Exception) {
            Toast.makeText(this, "مشکل در پیدا کردن ویوها: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

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

    // فقط فونت‌هایی که واقعا سالم هستن به لیست اضافه میشن (وگرنه توست خطا نشون میده)
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

    // استفاده از AmbilWarna برای ColorPicker
    fun openColorPicker(isText: Boolean) {
        val initialColor = if (isText) textColor else bgColor
        AmbilWarnaDialog(this, initialColor, object : AmbilWarnaDialog.OnAmbilWarnaListener {
            override fun onOk(dialog: AmbilWarnaDialog?, color: Int) {
                if (isText) {
                    applyColorToSelection(color)
                } else {
                    bgColor = color
                    renderTextImage()
                }
            }
            override fun onCancel(dialog: AmbilWarnaDialog?) {}
        }).show()
    }

    // رنگی کردن هر قسمت انتخابی از متن با Spannable
    fun applyColorToSelection(color: Int) {
        val start = etText.selectionStart
        val end = etText.selectionEnd
        if (start < end) {
            val spannable = etText.text as Spannable
            val span = ForegroundColorSpan(color)
            spannable.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            // اگر متنی انتخاب نشده بود رنگ کل متن عوض میشه
            textColor = color
            renderTextImage()
        }
    }

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
        paint.textAlign = Paint.Align.CENTER

        try {
            val tf = Typeface.createFromAsset(assets, "fonts/$currentFont")
            paint.typeface = tf
        } catch (e: Exception) {
            paint.typeface = Typeface.DEFAULT
            Toast.makeText(this, "مشکل در لود فونت (${currentFont}): ${e.message}", Toast.LENGTH_SHORT).show()
        }

        paint.color = textColor
        val text = etText.text.toString()
        val textLines = text.split("\n")
        val yStart = height / 2 - (textLines.size - 1) * fontSize * 1.5f
        for ((i, line) in textLines.withIndex()) {
            canvas.drawText(line, width / 2f, yStart + i * fontSize * 2.5f, paint)
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
