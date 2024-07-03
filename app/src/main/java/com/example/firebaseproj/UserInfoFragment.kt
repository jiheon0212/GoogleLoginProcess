package com.example.firebaseproj

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.Rotate
import com.bumptech.glide.request.RequestOptions
import com.example.firebaseproj.databinding.DialogImageBinding
import com.example.firebaseproj.databinding.FragmentUserInfoBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class UserInfoFragment : Fragment() {
    lateinit var fragmentUserInfoBinding: FragmentUserInfoBinding
    lateinit var reqGallery: ActivityResultLauncher<Intent>
    lateinit var reqCamera: ActivityResultLauncher<Intent>
    lateinit var filePath: String

    override fun onAttach(context: Context) {
        super.onAttach(context)
        cameraLauncher()
        galleryLauncher()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentUserInfoBinding = FragmentUserInfoBinding.inflate(layoutInflater, container, false)
        val view = fragmentUserInfoBinding.root

        return view
    }

    @SuppressLint("SimpleDateFormat")
    override fun onResume() {
        super.onResume()

        convertUserBirthDate()
        fragmentUserInfoBinding.btnSaveUserInfo.setOnClickListener {
            uploadBitmapFromUserImage(fragmentUserInfoBinding.userImage)
        }
        fragmentUserInfoBinding.userImage.setOnClickListener {
            imageDialog()
        }
    }

    // db collection에 uid별 document를 분리해서 저장하는 method
    private fun writeUserInfo(userImageFile: Uri) {
        val db = Firebase.firestore

        fragmentUserInfoBinding.run {
            // todo - 양식에 벗어난지 check하는 코드 작성해주기
            val userName = userName.text.toString()
            val userImage = userImageFile.toString()
            val userBirthDate = userBirthDate.text.toString()
            val userUid = Firebase.auth.uid

            db.collection("userInfoData")
                .document(userUid!!)
                .set(UserInfo(userName, userImage, userBirthDate))
                .addOnSuccessListener {
                    Toast.makeText(context, "successfully uploaded user information", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                    findNavController().navigate(R.id.myFragment)
                }
                .addOnFailureListener {
                    Toast.makeText(context, "$it", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // image 교체 dialog 생성 method
    private fun imageDialog() {
        val builder = AlertDialog.Builder(context)
        val dialogInflater = DialogImageBinding.inflate(layoutInflater)
        val dialogView = dialogInflater.root
        val group = dialogInflater.group

        builder.setTitle("이미지 변경")
        builder.setMessage("사진을 가져올 방법을 선택해주세요.")
        builder.setView(dialogView)

        builder.setPositiveButton("확인") { dialog, _ ->
            when (group.checkedRadioButtonId) {
                R.id.radio_camera -> {
                    getUserImageFromCamera()
                }
                R.id.radio_gallery -> {
                    getUserImageFromGallery()
                }
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("취소") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    // userImage를 firestore에 저장하고 해당 주소값을 return받아 userInfo dataclass에 저장해주는 방식
    private fun cameraLauncher() {
        reqCamera = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            try {
                val bitmap = BitmapFactory.decodeFile(filePath)
                // filepath를 디코딩하여 bitmap을 추출하는 방식으로 glide에 bitmap을 담아준다.
                Glide.with(requireContext())
                    .load(bitmap)
                    .override(150, 150)
                    .apply(RequestOptions().transform(Rotate(90)))
                    .into(fragmentUserInfoBinding.userImage)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun galleryLauncher() {
        reqGallery = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            try {
                val inputStream = context?.contentResolver?.openInputStream(it.data!!.data!!)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream!!.close()
                // inputStream을 디코딩하여 bitmap을 추출하는 방식으로 glide에 bitmap을 담아준다.
                Glide.with(requireContext())
                    .load(bitmap)
                    .override(150, 150)
                    .into(fragmentUserInfoBinding.userImage)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getUserImageFromCamera() {
        val timeStamp: String = SimpleDateFormat("yyMMdd_HHmmss", Locale.KOREA).format(Date())
        val storageDir: File? = context?.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File.createTempFile(
            "JPEG_${timeStamp}",
            ".jpg",
            storageDir
        )
        filePath = file.absolutePath
        val photoUri: Uri = FileProvider.getUriForFile(
            requireContext(),
            "com.example.firebaseproj.fileprovider", file
        )
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        reqCamera.launch(intent)
    }

    @SuppressLint("IntentReset")
    private fun getUserImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        reqGallery.launch(intent)
    }
    
    private fun uploadBitmapFromUserImage(imageView: ImageView) {
        val storage = Firebase.storage
        val userUid = Firebase.auth.uid
        val imageRef = storage.reference.child("images/$userUid.jpg")

        val imageViewResult = imageView.drawable as BitmapDrawable
        val uploadBitmap = imageViewResult.bitmap
        val byteOutputStream = ByteArrayOutputStream()
        uploadBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteOutputStream)

        val data = byteOutputStream.toByteArray()

        val uploadTask = imageRef.putBytes(data)
        uploadTask.addOnCompleteListener {
            if (it.isSuccessful) {
                Log.d("upload task success", "${imageRef.downloadUrl}")
                imageRef.downloadUrl.addOnSuccessListener {
                    writeUserInfo(it)
                    Log.d("uri load success", "$it")
                }
            } else {
                Log.d("upload task failed", "${it.exception}")
            }
        }
    }

    // userBirthDate를 string형식에서 Date로 format해주는 메서드
    private fun convertUserBirthDate() {
        fragmentUserInfoBinding.userBirthDate.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                // keyboard 전부 제거 후 dialog 띄우기
                fragmentUserInfoBinding.userBirthDate.inputType = InputType.TYPE_NULL
                
                val calendar = Calendar.getInstance()
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)

                val datePickerDialog = DatePickerDialog(
                    requireContext(),
                    { _, selectedYear, selectedMonth, selectedDay ->
                        val selectedDate = "${selectedYear}-${selectedMonth + 1}-${selectedDay}"
                        fragmentUserInfoBinding.userBirthDate.setText(selectedDate)
                    }, year, month, day
                )

                datePickerDialog.show()
            }
        }
    }
}