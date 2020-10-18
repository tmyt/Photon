package tech.onsen.photon.views.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_preview.view.*
import tech.onsen.photon.R
import java.io.File

class PreviewFragment : Fragment() {
    companion object {
        const val FILE_PATH = "LocalPath"

        fun newInstance(path: String): PreviewFragment {
            val fragment = PreviewFragment()
            fragment.arguments = Bundle().apply {
                putString(FILE_PATH, path)
            }
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val path = requireArguments().getString(FILE_PATH)!!
        Picasso.get()
            .load(File(path))
            //.load(path)
            .into(view.image)
    }
}