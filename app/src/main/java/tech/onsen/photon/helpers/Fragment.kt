package tech.onsen.photon.helpers

import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

fun Fragment.requireSupportActionBar(): ActionBar {
    return (requireActivity() as AppCompatActivity).supportActionBar!!
}