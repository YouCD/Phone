package org.fossify.phone.extensions

import android.content.Context
import org.fossify.commons.models.contacts.Contact

fun Contact.getDisplayName(): String {
    val firstName = firstName ?: ""
    val middleName = middleName ?: ""
    val surname = surname ?: ""
    val first = listOfNotNull(firstName, middleName).filter { it.isNotEmpty() }.joinToString(" ")

    return if (Contact.Companion.startWithSurname && surname.isNotEmpty()) {
        if (first.isNotEmpty()) "$surname$first" else surname
    } else {
        if (first.isNotEmpty()) "$first $surname".trim() else surname
    }
}
