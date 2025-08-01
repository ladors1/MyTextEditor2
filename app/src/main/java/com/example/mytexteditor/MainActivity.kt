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
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.ColorPickerDialog
import java.io.IOException

class MainActivity : AppCompatActivity() {

    // تعریف متغیرهای UI
    lateinit var etText: EditText
    lateinit var spinnerFont: Spinner
    lateinit var btnTextColor: Button
    lateinit var btnBgColor: Button
    lateinit var btnTransparentBg: Button
    lateinit var imagePreview: ImageView
    lateinit var btnSaveImage: Button
    lateinit var seekFontSize: SeekBar
    lateinit var tvFontSize: TextView

    // متغیرهای وضعیت
    var textColor = Color.BLACK // رنگ پیش‌فرض
    var bgColor = Color.WHITE
    var isTransparentBg = false
    var fontSize = 26f
    lateinit var fontList: List<String>
    var currentFont = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // اتصال متغیرها به UI
        etText = findViewById(R.id.etText)
        spinnerFont = findViewById(R.id.spinnerFont)
        btnTextColor = findViewById(R.id.btnTextColor)
        btnBgColor = findViewById(R.id.btnBgColor)
        btnTransparentBg = findViewById(R.id.btnTransparentBg)
        imagePreview = findViewById(R.id.imagePreview)
        btnSaveImage = findViewById(R.id.btnSaveImage)
        seekFontSize = findViewById(R.id.seekFontSize)
        tvFontSize = findViewById(R.id.tvFontSize)

        // لیسنر برای تغییرات متن که فقط پیش‌نمایش را آپدیت می‌کند
        etText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { renderTextImage() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // بارگذاری فونت‌ها
        setupFonts()

        // تنظیم اسپینر فونت
        setupFontSpinner()

        // تنظیم SeekBar برای اندازه فونت
        setupFontSizeSeeker()

        // تنظیم دکمه‌ها
        setupButtons()

        // رندر اولیه
        renderTextImage()
    }

    private fun setupFonts() {
        fontList = getFontList(this)
        if (fontList.isEmpty()) {
            Toast.makeText(this, "هیچ فونت سالمی پیدا نشد!", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        currentFont = fontList[0]
    }

    private fun setupFontSpinner() {
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
    }

    private fun setupFontSizeSeeker() {
        seekFontSize.progress = fontSize.toInt()
        tvFontSize.text = "اندازه فونت: ${fontSize.toInt()}"
        seekFontSize.setOnSeekBarChangeListener(object: OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                fontSize = if (progress > 0) progress.toFloat() else 1f
                tvFontSize.text = "اندازه فونت: $progress"
                renderTextImage()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupButtons() {
        btnTextColor.setOnClickListener { openColorPicker(isText = true) }
        btnBgColor.setOnClickListener { openColorPicker(isText = false) }
        btnTransparentBg.setOnClickListener {
            isTransparentBg = !isTransparentBg
            renderTextImage()
        }
        btnSaveImage.setOnClickListener { saveImage() }
    }

    private fun openColorPicker(isText: Boolean) {
        ColorPickerDialog.Builder(this)
            .setTitle(if (isText) "انتخاب رنگ متن" else "انتخاب رنگ پس‌زمینه")
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
    
    // *** روش نهایی و مستقیم برای اعمال رنگ ***
    private fun applyColorToSelection(color: Int) {
        val editable = etText.editableText
        val start = etText.selectionStart
        val end = etText.selectionEnd

        val targetStart = if (start == end) 0 else start
        val targetEnd = if (start == end) editable.length else end

        if (targetStart >= targetEnd) return

        // حذف رنگ‌های قبلی از محدوده
        val oldSpans = editable.getSpans(targetStart, targetEnd, ForegroundColorSpan::class.java)
        for (span in oldSpans) {
            editable.removeSpan(span)
        }

        // اعمال رنگ جدید
        editable.setSpan(ForegroundColorSpan(color), targetStart, targetEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }


    private fun renderTextImage() {
        val width = 1080
        val height = 1920
        val safeZone = 80f

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (isTransparentBg) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        } else {
            canvas.drawColor(bgColor)
        }
        
        // متن را از EditText دریافت می‌کنیم
        val textToRender: Spanned = etText.text

        // *** مرحله اشکال‌زدایی: بررسی تعداد رنگ‌های موجود در متن ***
        val spans = textToRender.getSpans(0, textToRender.length, ForegroundColorSpan::class.java)
        Toast.makeText(this, "Rendering with ${spans.size} color spans", Toast.LENGTH_SHORT).show()
        
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        textPaint.color = this.textColor // رنگ پیش‌فرض برای بخش‌های بدون رنگ خاص
        textPaint.textSize = fontSize * 2.5f

        try {
            val tf = Typeface.createFromAsset(assets, "fonts/$currentFont")
            textPaint.typeface = tf
        } catch (_: Exception) {
            textPaint.typeface = Typeface.DEFAULT
        }
        
        // StaticLayout خودش تمام Spanها را برای رنگ‌آمیزی متن تشخیص می‌دهد
        val textLayout = StaticLayout.Builder.obtain(textToRender, 0, textToRender.length, textPaint, width - (safeZone * 2).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.0f)
            .setIncludePad(true)
            .build()

        val textHeight = textLayout.height
        val yStart = (height - textHeight) / 2f

        canvas.save()
        canvas.translate(safeZone, yStart)
        textLayout.draw(canvas)
        canvas.restore()

        imagePreview.setImageBitmap(bmp)
    }

    private fun getFontList(context: Context): List<String> {
        return try {
            context.assets.list("fonts")
                ?.filter { it.endsWith(".ttf", true) || it.endsWith(".otf", true) }
                ?.filter {
                    try { Typeface.createFromAsset(context.assets, "fonts/$it"); true }
                    catch (e: Exception) { false }
                } ?: listOf()
        } catch (e: IOException) {
            listOf()
        }
    }
    
    private fun saveImage() {
        val drawable = imagePreview.drawable as? BitmapDrawable
        if (drawable == null) {
            Toast.makeText(this, "خطا: تصویر برای ذخیره آماده نیست.", Toast.LENGTH_SHORT).show()
            return
        }

        val bmp = drawable.bitmap
        val filename = "TextImage_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/MyTextEditor")
            }
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        try {
            uri?.let {
                resolver.openOutputStream(it)?.use { stream ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                Toast.makeText(this, "تصویر با موفقیت در گالری ذخیره شد!", Toast.LENGTH_LONG).show()
            } ?: throw IOException("خطا در ایجاد فایل در گالری")
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در ذخیره تصویر: ${e.message}", Toast.LENGTH_LONG).show()
            uri?.let { resolver.delete(it, null, null) }
        }
    }
}
