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
import androidx.core.view.WindowCompat
import android.view.View
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.ColorPickerDialog
import java.io.IOException

class MainActivity : AppCompatActivity() {

    // UI
    private lateinit var etText: TextInputEditText
    private lateinit var spinnerFont: Spinner
    private lateinit var btnTextColor: ImageButton
    private lateinit var btnBgColor: ImageButton
    private lateinit var btnTransparentBg: ImageButton
    private lateinit var imagePreview: ImageView
    private lateinit var btnSaveImage: FloatingActionButton
    private lateinit var seekFontSize: SeekBar
    private lateinit var tvFontSize: TextView

    // وضعیت
    private var bgColor = Color.WHITE
    private var isTransparentBg = false
    private var fontSizeInPx = 0f
    private var currentFontPath = ""
    private var fontList: Map<String, String> = mapOf()
    private var isProgrammaticTextChange = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_main)

        bindViews()
        setupFonts()
        setupListeners()
        // مقدار اولیه seekbar را تنظیم کن
        val initialFontSizeSp = 26f
        seekFontSize.progress = initialFontSizeSp.toInt()
        updateFontSize(initialFontSizeSp)

        renderTextImage(etText.text)
    }
    
    private fun updateFontSize(sp: Float) {
        fontSizeInPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
        tvFontSize.text = "اندازه فونت: ${sp.toInt()}"
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
                renderTextImage(etText.text)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        etText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { if (!isProgrammaticTextChange) renderTextImage(s) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        seekFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                updateFontSize(if (p > 0) p.toFloat() else 1f)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
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
            .setPositiveButton("تأیید",
                ColorEnvelopeListener { envelope, _ ->
                    val opaqueColor = (envelope.color and 0x00FFFFFF) or (0xFF000000).toInt()
                    if (isText) {
                        applyColorToSelection(opaqueColor)
                    } else {
                        bgColor = opaqueColor
                        renderTextImage(etText.text)
                    }
                })
            .setNegativeButton("انصراف") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun applyColorToSelection(color: Int) {
        val start = etText.selectionStart
        val end = etText.selectionEnd

        val newText = SpannableStringBuilder(etText.text)
        
        val targetStart = if (start == end) 0 else start
        val targetEnd = if (start == end) newText.length else end

        if (targetStart >= targetEnd && newText.isNotEmpty()) return

        newText.getSpans(targetStart, targetEnd, ForegroundColorSpan::class.java).forEach {
            newText.removeSpan(it)
        }
        newText.setSpan(ForegroundColorSpan(color), targetStart, targetEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        isProgrammaticTextChange = true
        val selectionStart = etText.selectionStart
        val selectionEnd = etText.selectionEnd
        etText.text = newText
        if (selectionStart >= 0 && selectionEnd >= 0) {
            etText.setSelection(selectionStart, selectionEnd)
        }
        isProgrammaticTextChange = false

        renderTextImage(newText)
    }

    // *** روش جدید و تضمینی رندر با تکنیک شابلون لایه‌لایه ***
    private fun renderTextImage(textToRender: CharSequence?) {
        val width = 1080
        val height = 1080
        val safeZone = 80f

        val finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val finalCanvas = Canvas(finalBitmap)

        if (isTransparentBg) {
            finalCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        } else {
            finalCanvas.drawColor(bgColor)
        }

        if (textToRender.isNullOrEmpty()) {
            imagePreview.setImageBitmap(finalBitmap); return
        }

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontSizeInPx
            textAlign = Paint.Align.CENTER
            try {
                typeface = Typeface.createFromAsset(assets, currentFontPath)
            } catch (e: Exception) {}
        }

        // متن را به خطوط تقسیم کن
        val textLines = textToRender.toString().split('\n')
        val textHeight = textPaint.descent() - textPaint.ascent()
        val totalTextHeight = textHeight * textLines.size
        var currentY = (height - totalTextHeight) / 2f - textPaint.ascent()

        if (textToRender is Spannable) {
            var charIndex = 0
            for (line in textLines) {
                // برای هر رنگ در خط، یک لایه جدا بساز
                val spans = textToRender.getSpans(charIndex, charIndex + line.length, ForegroundColorSpan::class.java)
                
                // 1. ابتدا کل خط را با رنگ پیش‌فرض (سیاه) بکش
                val lineBitmap = drawTextToBitmap(line, textPaint, width, textHeight.toInt(), Color.BLACK)
                finalCanvas.drawBitmap(lineBitmap, width / 2f - lineBitmap.width / 2f, currentY - textHeight, null)

                // 2. حالا برای هر رنگ، یک لایه جدید بساز و روی لایه قبلی بکش
                for (span in spans) {
                    val spanStart = textToRender.getSpanStart(span) - charIndex
                    val spanEnd = textToRender.getSpanEnd(span) - charIndex
                    if(spanStart < 0 || spanEnd > line.length) continue

                    val coloredSubstring = line.substring(spanStart, spanEnd)
                    val coloredBitmap = drawTextToBitmap(coloredSubstring, textPaint, width, textHeight.toInt(), span.foregroundColor)
                    
                    val precedingText = line.substring(0, spanStart)
                    val xOffset = textPaint.measureText(precedingText)

                    finalCanvas.drawBitmap(coloredBitmap, (width / 2f - textPaint.measureText(line)/2f) + xOffset , currentY - textHeight, null)
                }
                charIndex += line.length + 1
                currentY += textHeight
            }
        }
        imagePreview.setImageBitmap(finalBitmap)
    }

    private fun drawTextToBitmap(text: String, paint: TextPaint, canvasWidth: Int, canvasHeight: Int, color: Int): Bitmap {
        val textWidth = paint.measureText(text)
        val bitmap = Bitmap.createBitmap(textWidth.toInt().coerceAtLeast(1), canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val textMaskPaint = TextPaint(paint).apply { this.color = Color.BLACK }
        canvas.drawText(text, textWidth/2, canvasHeight/2f - ((paint.descent() + paint.ascent()) / 2f), textMaskPaint)

        val colorPaint = Paint().apply {
            this.color = color
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        canvas.drawPaint(colorPaint)
        return bitmap
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
