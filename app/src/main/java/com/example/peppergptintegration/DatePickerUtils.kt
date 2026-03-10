package com.example.peppergptintegration

import androidx.fragment.app.Fragment
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.*

object DatePickerUtils {

    fun showMaterialDatePicker(
        fragment: Fragment,
        title: String,
        initialDate: Long? = null,
        onDateSelected: (Long) -> Unit
    ) {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(title)
            .apply {
                initialDate?.let { setSelection(it) }
            }
            .build()

        datePicker.addOnPositiveButtonClickListener { timestamp ->
            onDateSelected(timestamp)
        }

        datePicker.show(fragment.childFragmentManager, "DATE_PICKER_TAG")
    }

    fun formatDate(timestamp: Long): String {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.timeInMillis = timestamp
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return format.format(utc.time)
    }
}
