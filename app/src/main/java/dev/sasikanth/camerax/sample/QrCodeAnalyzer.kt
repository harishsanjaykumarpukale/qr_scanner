package dev.sasikanth.camerax.sample

import android.graphics.Bitmap
import android.graphics.ImageFormat.*
import android.os.Build
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.nio.ByteBuffer
import org.opencv.core.Core

import org.opencv.imgproc.Imgproc




private fun ByteBuffer.toByteArray(): ByteArray {
    rewind()
    val data = ByteArray(remaining())
    get(data)
    return data
}

class QrCodeAnalyzer(
    private val onQrCodesDetected: (qrCode: Result) -> Unit
) : ImageAnalysis.Analyzer {

    private val yuvFormats = mutableListOf(YUV_420_888)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            yuvFormats.addAll(listOf(YUV_422_888, YUV_444_888))
        }
    }

    private val reader = MultiFormatReader().apply {
        val map = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to arrayListOf(BarcodeFormat.QR_CODE)
        )
        setHints(map)
    }

    private fun ImageProxy.convertYUVtoMat() : Mat{
        val yBuffer = planes[0].buffer // Y
        val uBuffer = planes[1].buffer // U
        val vBuffer = planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = Mat(this.height + this.height/2, this.width, CvType.CV_8UC1)
        yuvImage.put(0,0,nv21)
        val rgb = Mat()
        Imgproc.cvtColor(yuvImage, rgb, Imgproc.COLOR_YUV2RGB_NV21, 3)
        Core.rotate(rgb, rgb, Core.ROTATE_90_CLOCKWISE)
        return rgb
    }

    override fun analyze(image: ImageProxy) {
        // We are using YUV format because, ImageProxy internally uses ImageReader to get the image
        // by default ImageReader uses YUV format unless changed.
        if (image.format !in yuvFormats) {
            Log.e("QRCodeAnalyzer", "Expected YUV, now = ${image.format}")
            return
        }

//        val data = image.planes[0].buffer.toByteArray()
//
//        val source = PlanarYUVLuminanceSource(
//            data,
//            image.width,
//            image.height,
//            0,
//            0,
//            image.width,
//            image.height,
//            false
//        )

        val src = image.convertYUVtoMat()
        val gray = Mat()

        Imgproc.cvtColor(src,gray,Imgproc.COLOR_RGB2GRAY)

        // destination has grayscale

        val final = Mat()

        Core.bitwise_not(gray,final)

        val bmp = Bitmap.createBitmap(final.width(),final.height(),Bitmap.Config.RGB_565)
        Utils.matToBitmap(final,bmp)

        val intArray = IntArray(bmp.width * bmp.height)
        bmp.getPixels(intArray, 0, bmp.width, 0, 0, bmp.width, bmp.height)

        val source: LuminanceSource = RGBLuminanceSource(bmp.width, bmp.height, intArray)



        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        try {
            // Whenever reader fails to detect a QR code in image
            // it throws NotFoundException
            val result = reader.decode(binaryBitmap)
            onQrCodesDetected(result)
        } catch (e: NotFoundException) {
            e.printStackTrace()
        }
        image.close()
    }
}
