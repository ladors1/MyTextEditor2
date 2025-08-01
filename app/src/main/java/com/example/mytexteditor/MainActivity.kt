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
    var textColor = Color.BLACK
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

        // تنظیم رنگ اولیه EditText
        etText.setTextColor(textColor)

        // بارگذاری فونت‌ها
        fontList = getFontList(this)
        if (fontList.isEmpty()) {
            Toast.makeText(this, "هیچ فونت سالمی پیدا نشد! فونت مناسب در assets/fonts قرار بده.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        currentFont = fontList[0]

        // تنظیم اسپینر فونت
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

        // لیسنر برای تغییرات متن
        etText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // بعد از هر تغییری، چه متن و چه رنگ، پیش‌نمایش به‌روز می‌شود
                renderTextImage()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // تنظیم SeekBar برای اندازه فونت
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

        // تنظیم دکمه‌ها
        btnTextColor.setOnClickListener { openColorPicker(isText = true) }
        btnBgColor.setOnClickListener { openColorPicker(isText = false) }
        btnTransparentBg.setOnClickListener {
            isTransparentBg = !isTransparentBg
            btnTransparentBg.text = if (isTransparentBg) "پس‌زمینه رنگی" else "پس‌زمینه شفاف"
            renderTextImage()
        }
        btnSaveImage.setOnClickListener { saveImage() }

        // رندر اولیه
        renderTextImage()
    }

    private fun getFontList(context: Context): List<String> {
        val allFonts = try {
            context.assets.list("fonts")?.filter { it.endsWith(".ttf", true) || it.endsWith(".otf", true) } ?: listOf()
        } catch (e: IOException) {
            listOf()
        }
        val validFonts = mutableListOf<String>()
        for (f in allFonts) {
            try {
                Typeface.createFromAsset(context.assets, "fonts/$f")
                validFonts.add(f)
            } catch (e: Exception) {
                // نادیده گرفتن فونت‌های خراب
            }
        }
        return validFonts
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

    // *** اصلاح شده: منطق اعمال رنگ فقط با Spannable ***
    private fun applyColorToSelection(color: Int) {
        val spannable = etText.text as Spannable
        val start = etText.selectionStart
        val end = etText.selectionEnd

        // اگر متنی انتخاب نشده باشد، کل متن را هدف قرار بده
        val targetStart = if (start == end) 0 else start
        val targetEnd = if (start == end) etText.length() else end

        // اگر هیچ متنی در ادیتور وجود ندارد، رنگ پیش‌فرض را برای نوشته‌های بعدی تنظیم کن
        if (etText.length() == 0) {
            textColor = color
            etText.setTextColor(textColor) // برای رنگ کرسر و متن آینده
            return
        }
        
        // اگر محدوده معتبر است
        if(targetStart < targetEnd) {
             // ابتدا تمام Spanهای رنگی قبلی را در محدوده مورد نظر حذف کن
            val oldSpans = spannable.getSpans(targetStart, targetEnd, ForegroundColorSpan::class.java)
            for (span in oldSpans) {
                spannable.removeSpan(span)
            }
            // Span رنگی جدید را اعمال کن
            spannable.setSpan(ForegroundColorSpan(color), targetStart, targetEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        // اگر کل متن رنگ شده، رنگ پیش‌فرض را هم آپدیت کن
        if (start == end) {
            textColor = color
        }
    }

    // *** اصلاح شده: رندر با اطمینان بالا ***
    private fun renderTextImage() {
        val width = 1080
        val height = 1920
        val safeZone = 80f

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (isTransparentBg) {
            // برای اطمینان از شفافیت کامل، بوم را کاملاً پاک می‌کنیم
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        } else {
            canvas.drawColor(bgColor)
        }

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        textPaint.color = this.textColor // رنگ پیش‌فرض برای بخش‌هایی که Span ندارند
        textPaint.textSize = fontSize * 2.5f

        try {
            val tf = Typeface.createFromAsset(assets, "fonts/$currentFont")
            textPaint.typeface = tf
        } catch (_: Exception) {
            textPaint.typeface = Typeface.DEFAULT
        }

        // StaticLayout خودش تمام Spanها را برای رنگ‌آمیزی متن تشخیص می‌دهد
        val textLayout = StaticLayout.Builder.obtain(etText.text, 0, etText.text.length, textPaint, width - (safeZone * 2).toInt())
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

    // *** اصلاح شده: ذخیره صحیح تصویر PNG با شفافیت ***
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
                val outputStream = resolver.openOutputStream(it)
                outputStream.use { stream ->
                    if (stream == null) throw IOException("Failed to get output stream.")
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                Toast.makeText(this, "تصویر با موفقیت در گالری ذخیره شد!", Toast.LENGTH_LONG).show()
            } ?: throw IOException("خطا در ایجاد فایل در گالری")
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در ذخیره تصویر: ${e.message}", Toast.LENGTH_LONG).show()
            uri?.let { resolver.delete(it, null, null) } // پاک کردن فایل ناقص در صورت خطا
        }
    }
}
