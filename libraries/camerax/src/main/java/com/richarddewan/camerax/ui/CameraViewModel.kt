package com.richarddewan.camerax.ui

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel


/*
created by Richard Dewan 13/10/2020
*/

class CameraViewModel: ViewModel() {
    companion object {
        private const val TAG = "CameraViewModel"
    }

    val isLoading: MutableLiveData<Boolean> = MutableLiveData()
    val lensFacing: MutableLiveData<Int> = MutableLiveData(1)

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG,"CameraViewModel onCleared")
    }
}