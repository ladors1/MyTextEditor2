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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // اتصال UI
        bindViews()

        // راه‌اندازی برنامه
        setupFonts()
        setupListeners()

        // رندر اولیه
        renderTextImage(etText.text)
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
            finish()
            return
        }
        val fontNames = fontList.keys.toList()
        currentFontPath = fontList[fontNames[0]].orEmpty()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fontNames)
        spinnerFont.adapter = adapter
    }

    private fun setupListeners() {
        spinnerFont.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedFontName = parent.getItemAtPosition(position) as String
                currentFontPath = fontList[selectedFontName].orEmpty()
                renderTextImage(etText.text)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        etText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isProgrammaticTextChange) {
                    renderTextImage(s)
                }
            }
        })

        seekFontSize.progress = fontSizeInSp.toInt()
        tvFontSize.text = "اندازه فونت: ${fontSizeInSp.toInt()}"
        seekFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                fontSizeInSp = if (progress > 0) progress.toFloat() else 1f
                tvFontSize.text = "اندازه فونت: $progress"
                etText.textSize = fontSizeInSp // تغییر اندازه فونت در ادیتور
                renderTextImage(etText.text)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnTextColor.setOnClickListener { openColorPicker(true) }
        btnBgColor.setOnClickListener { openColorPicker(false) }
        btnTransparentBg.setOnClickListener {
            isTransparentBg = !isTransparentBg
            renderTextImage(etText.text)
        }
        btnSaveImage.setOnClickListener { saveImage() }
    }

    private fun openColorPicker(isText: Boolean) {
        ColorPickerDialog.Builder(this)
            .setTitle(if (isText) "انتخاب رنگ متن" else "انتخاب رنگ پس‌زمینه")
            .setPositiveButton("تأیید") { _, envelope: ColorEnvelope ->
                if (isText) {
                    applyColorToSelection(envelope.color)
                } else {
                    bgColor = envelope.color
                    renderTextImage(etText.text)
                }
            }
            .setNegativeButton("انصراف") { dialogInterface, _ -> dialogInterface.dismiss() }
            .show()
    }

    private fun applyColorToSelection(color: Int) {
        val start = etText.selectionStart
        val end = etText.selectionEnd

        val newText = SpannableStringBuilder(etText.text)
        val targetStart = if (start == end) 0 else start
        val targetEnd = if (start == end) newText.length else end

        if (targetStart >= targetEnd) return

        // پاک کردن رنگ‌های قبلی
        newText.getSpans(targetStart, targetEnd, ForegroundColorSpan::class.java).forEach {
            newText.removeSpan(it)
        }
        // اعمال رنگ جدید
        newText.setSpan(ForegroundColorSpan(color), targetStart, targetEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        isProgrammaticTextChange = true
        etText.text = newText
        etText.setSelection(end)
        isProgrammaticTextChange = false

        // فراخوانی مستقیم رندر با متن جدید برای اطمینان
        renderTextImage(newText)
    }

    // *** قلب تپنده برنامه: رندر کردن با یک TextView واقعی اما نامرئی ***
    private fun renderTextImage(textToRender: CharSequence?) {
        val width = 1080
        val height = 1920
        val safeZone = 80f

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // رسم پس‌زمینه
        if (isTransparentBg) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        } else {
            canvas.drawColor(bgColor)
        }

        // اگر متنی برای رندر وجود ندارد، خارج شو
        if (textToRender.isNullOrEmpty()) {
            imagePreview.setImageBitmap(bmp)
            return
        }

        // ساخت یک TextView در حافظه برای رندر دقیق
        val textView = TextView(this).apply {
            // اعمال متن رنگی شده
            text = textToRender
            // اعمال اندازه فونت
            setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeInSp)
            // اعمال فونت سفارشی
            try {
                typeface = Typeface.createFromAsset(context.assets, currentFontPath)
            } catch (e: Exception) {
                // در صورت مشکل، از فونت پیش‌فرض استفاده کن
            }
            // تنظیمات دیگر
            setTextColor(Color.BLACK) // رنگ پایه (Spanها روی این رنگ می‌نشینند)
            gravity = android.view.Gravity.CENTER
        }

        // اندازه‌گیری TextView
        val widthSpec = View.MeasureSpec.makeMeasureSpec((width - 2 * safeZone).toInt(), View.MeasureSpec.AT_MOST)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST)
        textView.measure(widthSpec, heightSpec)
        textView.layout(0, 0, textView.measuredWidth, textView.measuredHeight)

        // قرار دادن TextView در مرکز بوم و نقاشی کردن آن
        canvas.save()
        val xPos = (width - textView.measuredWidth) / 2f
        val yPos = (height - textView.measuredHeight) / 2f
        canvas.translate(xPos, yPos)
        textView.draw(canvas)
        canvas.restore()

        // نمایش نتیجه در ImageView
        imagePreview.setImageBitmap(bmp)
    }

    private fun getFontList(context: Context): Map<String, String> {
        return try {
            val fontPaths = context.assets.list("fonts")
                ?.filter { it.endsWith(".ttf", true) || it.endsWith(".otf", true) } ?: emptyList()
            fontPaths.associateBy(
                keySelector = { it.substringBeforeLast('.') }, // نام فایل بدون پسوند
                valueTransform = { "fonts/$it" } // مسیر کامل در assets
            )
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
                contentResolver.openOutputStream(uri)?.use { stream ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                Toast.makeText(this, "تصویر با موفقیت ذخیره شد!", Toast.LENGTH_LONG).show()
            }
        } catch (e: IOException) {
            Toast.makeText(this, "خطا در ذخیره تصویر: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
