package com.smartexpenseai.app.ui.tags

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.smartexpenseai.app.data.dao.TagWithCount
import com.smartexpenseai.app.data.entities.TagEntity
import com.smartexpenseai.app.data.repository.TagRepository
import com.smartexpenseai.app.databinding.FragmentTagManagementBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TagManagementFragment : Fragment() {

    private var _binding: FragmentTagManagementBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var tagRepository: TagRepository

    private lateinit var adapter: TagAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTagManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TagAdapter(onEdit = ::showEditDialog, onDelete = ::confirmDelete)
        binding.recyclerTags.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerTags.adapter = adapter

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.fabAddTag.setOnClickListener { showCreateDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tagRepository.getTagsWithCounts().collectLatest { tags ->
                    adapter.submitList(tags)
                    binding.tvEmpty.visibility = if (tags.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun showCreateDialog() {
        val input = TextInputEditText(requireContext()).apply { hint = "Tag name" }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("New tag")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "Enter a tag name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                launchIo { tagRepository.getOrCreateTag(name) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(item: TagWithCount) {
        val tag = item.tag
        val input = TextInputEditText(requireContext()).apply { setText(tag.name) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit tag")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isEmpty()) {
                    Toast.makeText(requireContext(), "Enter a tag name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = tagRepository.renameTag(tag, newName)
                    if (!ok) {
                        Toast.makeText(requireContext(), "A tag named \"$newName\" already exists", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNeutralButton("Color…") { _, _ -> showColorDialog(tag) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showColorDialog(tag: TagEntity) {
        val names = PALETTE.map { it.first }.toTypedArray()
        val selected = PALETTE.indexOfFirst { it.second.equals(tag.color, ignoreCase = true) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Tag color")
            .setSingleChoiceItems(names, selected) { dialog, which ->
                launchIo { tagRepository.updateTagColor(tag, PALETTE[which].second) }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(item: TagWithCount) {
        val used = item.usageCount
        val message = if (used > 0) {
            "Delete \"${item.tag.name}\"? It will be removed from $used transaction${if (used == 1) "" else "s"}."
        } else {
            "Delete \"${item.tag.name}\"?"
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete tag")
            .setMessage(message)
            .setPositiveButton("Delete") { _, _ -> launchIo { tagRepository.deleteTag(item.tag) } }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchIo(block: suspend () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch { block() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        // Preset palette for tag colors (label to hex).
        private val PALETTE = listOf(
            "Grey" to "#607D8B",
            "Red" to "#F44336",
            "Orange" to "#FF9800",
            "Green" to "#4CAF50",
            "Blue" to "#2196F3",
            "Purple" to "#9C27B0",
            "Teal" to "#009688",
            "Pink" to "#E91E63"
        )
    }
}
