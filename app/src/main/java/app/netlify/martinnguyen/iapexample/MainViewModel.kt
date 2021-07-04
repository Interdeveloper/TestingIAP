package app.netlify.martinnguyen.iapexample

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    val _isPremium = MutableLiveData(false)
    val isPremium: LiveData<Boolean> = _isPremium

    fun setIsPremium(isPremium: Boolean) {
        _isPremium.value = isPremium
    }
}