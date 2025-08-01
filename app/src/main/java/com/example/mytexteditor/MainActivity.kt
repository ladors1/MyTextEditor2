package com.example.mytexteditor

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
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
        etText.setTextColor(textColor) // ست کردن رنگ اولیه

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

        btnTextColor.setOnClickListener { openColorPicker() }
        btnBgColor.setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setTitle("انتخاب رنگ پس‌زمینه")
                .setPreferenceName("BgColorPickerDialog")
                .setPositiveButton("تأیید",
                    ColorEnvelopeListener { envelope, _ ->
                        bgColor = envelope.color
                        renderTextImage()
                    })
                .setNegativeButton("انصراف") { dialogInterface, _ -> dialogInterface.dismiss() }
                .show()
        }
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

    fun openColorPicker() {
        ColorPickerDialog.Builder(this)
            .setTitle("انتخاب رنگ متن")
            .setPreferenceName("TextColorPickerDialog")
            .setPositiveButton("تأیید",
                ColorEnvelopeListener { envelope, _ ->
                    applyColorToSelection(envelope.color)
                })
            .setNegativeButton("انصراف") { dialogInterface, _ -> dialogInterface.dismiss() }
            .show()
    }

    // **اصلاح شده برای اعمال صحیح رنگ**
    fun applyColorToSelection(color: Int) {
        val start = etText.selectionStart
        val end = etText.selectionEnd
        val spannable = etText.text as Spannable

        val selectionStart: Int
        val selectionEnd: Int

        // اگر متنی انتخاب نشده بود، کل متن را هدف قرار بده
        if (start == end && etText.length() > 0) {
            selectionStart = 0
            selectionEnd = etText.length()
            // رنگ پیش‌فرض کلی را هم به‌روز کن
            textColor = color
        } else {
            selectionStart = start
            selectionEnd = end
        }

        // اگر محدوده معتبری برای رنگ کردن وجود دارد
        if (selectionStart < selectionEnd) {
            // ابتدا رنگ‌های قبلی در آن محدوده را پاک کن
            val oldSpans = spannable.getSpans(selectionStart, selectionEnd, ForegroundColorSpan::class.java)
            for (span in oldSpans) {
                spannable.removeSpan(span)
            }
            // سپس رنگ جدید را اعمال کن
            spannable.setSpan(ForegroundColorSpan(color), selectionStart, selectionEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else if (start == end && etText.length() == 0) {
             // اگر متنی وجود ندارد، فقط رنگ پیش‌فرض را تغییر بده
            textColor = color
            etText.setTextColor(textColor)
        }


        // به‌روزرسانی پیش‌نمایش
        renderTextImage()
    }

    // استفاده از StaticLayout برای رندر صحیح
    fun renderTextImage() {
        val width = 1080
        val height = 1920
        val safeZone = 80f

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (!isTransparentBg) {
            canvas.drawColor(bgColor)
        }

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        textPaint.color = textColor // رنگ پیش‌فرض برای بخش‌های بدون Span
        textPaint.textSize = fontSize * 2.5f

        try {
            val tf = Typeface.createFromAsset(assets, "fonts/$currentFont")
            textPaint.typeface = tf
        } catch (_: Exception) {
            textPaint.typeface = Typeface.DEFAULT
        }

        val text: Spanned = etText.text

        val textLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width - (safeZone * 2).toInt())
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

    // **اصلاح شده برای ذخیره صحیح شفافیت**
    fun saveImage() {
        try {
            val drawable = imagePreview.drawable
            if (drawable is BitmapDrawable) {
                val bmp = drawable.bitmap
                MediaStore.Images.Media.insertImage(
                    contentResolver, bmp, "TextImage", "متن ذخیره شده"
                )
                Toast.makeText(this, "تصویر ذخیره شد و قابل استفاده برای استوری یا استیکر!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "خطا: بیت‌مپ تصویر پیدا نشد.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در ذخیره تصویر: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
