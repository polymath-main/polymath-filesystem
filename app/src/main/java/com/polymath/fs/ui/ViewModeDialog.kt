package com.polymath.fs.ui

import android.content.Context
import android.view.LayoutInflater
import android.widget.RadioButton
import android.widget.RadioGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.polymath.fs.R
import com.polymath.fs.models.BoxSize
import com.polymath.fs.models.IconPack
import com.polymath.fs.models.ViewLayout
import com.polymath.fs.models.ViewOptions
import com.polymath.fs.viewmodels.FileSystemViewModel

object ViewModeDialog {

    fun show(
        context: Context,
        currentOptions: ViewOptions,
        onApply: (ViewOptions) -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_view_mode, null)

        val rgLayout = view.findViewById<RadioGroup>(R.id.rg_layout)
        val rgColumns = view.findViewById<RadioGroup>(R.id.rg_columns)
        val rgBoxSize = view.findViewById<RadioGroup>(R.id.rg_box_size)
        val switchDetails = view.findViewById<MaterialSwitch>(R.id.switch_show_details)
        val rgOrientation = view.findViewById<RadioGroup>(R.id.rg_orientation)

        // Pre-select current layout
        when (currentOptions.layout) {
            ViewLayout.LIST -> view.findViewById<RadioButton>(R.id.rb_layout_list)?.isChecked = true
            ViewLayout.GRID -> view.findViewById<RadioButton>(R.id.rb_layout_grid)?.isChecked = true
            ViewLayout.HORIZONTAL -> view.findViewById<RadioButton>(R.id.rb_layout_horizontal)?.isChecked = true
        }

        // Pre-select columns
        when (currentOptions.columns) {
            2 -> view.findViewById<RadioButton>(R.id.rb_col_2)?.isChecked = true
            4 -> view.findViewById<RadioButton>(R.id.rb_col_4)?.isChecked = true
            else -> view.findViewById<RadioButton>(R.id.rb_col_3)?.isChecked = true
        }

        // Pre-select orientation
        if (currentOptions.isVertical) {
            view.findViewById<RadioButton>(R.id.rb_orient_vertical)?.isChecked = true
        } else {
            view.findViewById<RadioButton>(R.id.rb_orient_horizontal)?.isChecked = true
        }

        // Pre-select box size
        when (currentOptions.boxSize) {
            BoxSize.SMALL -> view.findViewById<RadioButton>(R.id.rb_box_small)?.isChecked = true
            BoxSize.MEDIUM -> view.findViewById<RadioButton>(R.id.rb_box_medium)?.isChecked = true
            BoxSize.LARGE -> view.findViewById<RadioButton>(R.id.rb_box_large)?.isChecked = true
            BoxSize.EXTRA_LARGE -> view.findViewById<RadioButton>(R.id.rb_box_xlarge)?.isChecked = true
        }

        switchDetails?.isChecked = currentOptions.showDetails

        MaterialAlertDialogBuilder(context)
            .setTitle("Directory View Options")
            .setView(view)
            .setPositiveButton("Apply") { _, _ ->
                val chosenLayout = when (rgLayout.checkedRadioButtonId) {
                    R.id.rb_layout_grid -> ViewLayout.GRID
                    R.id.rb_layout_horizontal -> ViewLayout.HORIZONTAL
                    else -> ViewLayout.LIST
                }

                val chosenColumns = when (rgColumns.checkedRadioButtonId) {
                    R.id.rb_col_2 -> 2
                    R.id.rb_col_4 -> 4
                    else -> 3
                }

                val isVertical = rgOrientation.checkedRadioButtonId != R.id.rb_orient_horizontal

                val chosenBoxSize = when (rgBoxSize.checkedRadioButtonId) {
                    R.id.rb_box_small -> BoxSize.SMALL
                    R.id.rb_box_large -> BoxSize.LARGE
                    R.id.rb_box_xlarge -> BoxSize.EXTRA_LARGE
                    else -> BoxSize.MEDIUM
                }

                val showDetails = switchDetails?.isChecked ?: true

                val newOptions = currentOptions.copy(
                    layout = chosenLayout,
                    isVertical = isVertical,
                    columns = chosenColumns,
                    boxSize = chosenBoxSize,
                    showDetails = showDetails
                )
                onApply(newOptions)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
