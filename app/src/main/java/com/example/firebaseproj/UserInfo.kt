package com.example.firebaseproj

import android.net.Uri

// ? = null을 통해 역직렬화가 가능해져 toObject를 통해 document에서 dataclass로 바로 결과 값을 전달받는다.
data class UserInfo(
    val userName: String? = null,
    val userFirstImage: String? = null,
    val userBirthDate: String? = null
)
