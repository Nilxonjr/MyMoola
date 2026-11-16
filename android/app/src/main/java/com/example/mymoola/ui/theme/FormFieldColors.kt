package com.example.mymoola.ui.theme

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrandInputTextColor = Color(0xFF0F172A)
private val BrandInputMutedColor = Color(0xFF64748B)

@Composable
fun myMoolaOutlinedTextFieldColors(
    focusedBorderColor: Color = Color(0xFF0A7C6A),
    unfocusedBorderColor: Color = Color(0xFFE2E8F0),
    focusedContainerColor: Color = Color.Unspecified,
    unfocusedContainerColor: Color = Color.Unspecified,
    focusedTrailingIconColor: Color = Color(0xFF0A7C6A),
    unfocusedTrailingIconColor: Color = BrandInputMutedColor
): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = BrandInputTextColor,
        unfocusedTextColor = BrandInputTextColor,
        focusedLabelColor = BrandInputMutedColor,
        unfocusedLabelColor = BrandInputMutedColor,
        focusedPlaceholderColor = BrandInputMutedColor,
        unfocusedPlaceholderColor = BrandInputMutedColor,
        cursorColor = BrandInputTextColor,
        focusedBorderColor = focusedBorderColor,
        unfocusedBorderColor = unfocusedBorderColor,
        focusedContainerColor = focusedContainerColor,
        unfocusedContainerColor = unfocusedContainerColor,
        focusedTrailingIconColor = focusedTrailingIconColor,
        unfocusedTrailingIconColor = unfocusedTrailingIconColor
    )
}
