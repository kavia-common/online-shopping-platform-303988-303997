package org.example.app.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import org.example.app.R

class HomeFragment : Fragment(R.layout.fragment_home) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialButton>(R.id.btn_browse_catalog).setOnClickListener {
            findNavController().navigate(R.id.action_home_to_catalog)
        }

        view.findViewById<MaterialButton>(R.id.btn_view_cart).setOnClickListener {
            findNavController().navigate(R.id.action_home_to_cart)
        }
    }
}
