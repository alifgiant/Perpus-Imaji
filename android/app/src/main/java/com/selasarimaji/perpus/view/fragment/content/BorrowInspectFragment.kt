package com.selasarimaji.perpus.view.fragment.content

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.support.design.widget.TextInputLayout
import android.text.InputType
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import com.jakewharton.rxbinding2.widget.RxTextView
import com.selasarimaji.perpus.*
import com.selasarimaji.perpus.model.RepoDataModel
import com.selasarimaji.perpus.viewmodel.content.BorrowVM
import kotlinx.android.synthetic.main.layout_content_creation.*
import kotlinx.android.synthetic.main.content_borrow.*
import java.util.*
import java.util.concurrent.TimeUnit

class BorrowInspectFragment : BaseInspectFragment<RepoDataModel.Borrow>() {

    override val viewModel by lazy {
        ViewModelProviders.of(activity!!).get(BorrowVM::class.java)
    }

    private val kidText : String
        get() = borrowNameInputLayout.editText?.text.toString()

    private val bookText : String
        get() = borrowBookInputLayout.editText?.text.toString()

    override fun setupView(){
        val view = layoutInflater.inflate(R.layout.content_borrow, null)
        linearContainer.addView(view, 0)

        borrowStartDateInputLayout.editText?.run {
            setText(getCurrentDateString(0))
            showDatePickerOnClick(this, 0)
        }
        borrowEndDateInputLayout.editText?.run {
            setText(getCurrentDateString(3))
            showDatePickerOnClick(this, 3)
        }

        borrowNameInputLayout.editText?.let {
            RxTextView.textChanges(it)
                    .skip(1)
                    .debounce(300, TimeUnit.MILLISECONDS)
                    .subscribe {
                        viewModel.getPossibleKidName(it)
                    }
        }

        borrowBookInputLayout.editText?.let {
            RxTextView.textChanges(it)
                    .skip(1)
                    .debounce(300, TimeUnit.MILLISECONDS)
                    .subscribe {
                        viewModel.getPossibleBookName(it)
                    }
        }
    }

    override fun setupToolbar(){
        viewModel.title.value = "Peminjaman"
        viewModelInspect.getSelectedItemLiveData().observe(this, Observer {
            (it as RepoDataModel.Borrow?)?.let {
                viewModel.title.value = it.id.toUpperCase()
            }
        })
    }

    override fun setupObserver(){
        viewModelInspect.getSelectedItemLiveData().observe(this, Observer {
            (it as RepoDataModel.Borrow?)?.let {
                borrowBookInputLayout.editText?.setText(it.idBook)
                borrowNameInputLayout.editText?.setText(it.idChild)
                borrowStartDateInputLayout.editText?.setText(it.startDate)
                borrowEndDateInputLayout.editText?.setText(it.endDate)
            }
        })

        viewModelInspect.editOrCreateMode.observe(this, Observer {
            addButton.visibility = if (it?.second != true) View.GONE else View.VISIBLE
        })

        viewModelInspect.editOrCreateMode.observe(this, Observer {
            arrayListOf<TextInputLayout>(borrowBookInputLayout,
                    borrowNameInputLayout,
                    borrowStartDateInputLayout,
                    borrowEndDateInputLayout)
                    .apply {
                        if (it?.first != true) {
                            this.map {
                                it.editText?.inputType = InputType.TYPE_NULL
                            }
                        } else {
                            this.map {
                                it.editText?.inputType = InputType.TYPE_CLASS_TEXT
                            }
                        }
                        this[2].isEnabled = it?.first == true
                        this[3].isEnabled = it?.first == true
                    }
        })
        viewModel.isLoading.observe(this, Observer {
            addButton.isEnabled = !(it ?: false)
        })
        viewModel.shouldFinish.observe(this, Observer {
            if (it == true){
                activity?.let {
                    it.setResult(Activity.RESULT_OK)
                    it.finish()
                }
            }
        })
        viewModel.repoKidVal.fetchedData.observe(this, Observer {
            it?.run {
                val adapter = ArrayAdapter<String>(context,
                        android.R.layout.simple_dropdown_item_1line,
                        this.filter { it.name.contains(kidText) }.map { it.name.capitalizeWords() })
                (borrowNameInputLayout.editText as AutoCompleteTextView).run {
                    setAdapter(adapter)
                    showDropDown()
                }
            }
        })
        viewModel.repoBookVal.fetchedData.observe(this, Observer {
            it?.run {
                val adapter = ArrayAdapter<String>(context,
                        android.R.layout.simple_dropdown_item_1line,
                        this.filter { it.name.contains(bookText) }.map { it.name.capitalizeWords() })
                (borrowBookInputLayout.editText as AutoCompleteTextView).run {
                    setAdapter(adapter)
                    showDropDown()
                }
            }
        })
    }

    override fun createValue(): RepoDataModel.Borrow? {
        val editTextList = arrayListOf<TextInputLayout>(borrowBookInputLayout,
                borrowNameInputLayout, borrowStartDateInputLayout,
                borrowEndDateInputLayout).apply {
            this.map {
                it.error = null
                it.isErrorEnabled = false
            }
        }
        val bookName = viewModel.repoBookVal.fetchedData.value?.find {
            it.name == bookText.toLowerCase()
        }?.id.also {
            if (!it.isNullOrEmpty()) {
                editTextList.remove(borrowBookInputLayout)
            }
        }

        val borrower = viewModel.repoKidVal.fetchedData.value?.find {
            it.name == kidText.toLowerCase()
        }?.id.also {
            if (!it.isNullOrEmpty()) {
                editTextList.remove(borrowNameInputLayout)
            }
        }
        val startDate = borrowStartDateInputLayout.tryToRemoveFromList(editTextList)
        val endDate = borrowEndDateInputLayout.tryToRemoveFromList(editTextList)
        if (parseDateString(endDate) < parseDateString(startDate)) {
            editTextList.add(borrowEndDateInputLayout)
            borrowEndDateInputLayout.error = "Tanggal kembali harus lebih besar"
        }

        editTextList.map {
            if (it.error.isNullOrEmpty()) it.error = "Silahkan diisi"
        }

        return if(editTextList.isEmpty())
            RepoDataModel.Borrow(bookName!!, borrower!!, startDate, endDate)
        else
            null
    }

    override fun submitValue() {
        createValue()?.let {
            viewModel.storeData(it){
                if(it.isSuccess) {
                    showLoadingResultToast(it.loadingType)
                    viewModel.shouldFinish.value = true
                } else {
                    showErrorConnectionToast()
                }
            }
        }
    }

    private fun showDatePickerOnClick(editText: EditText, dayAhead : Int){
        val c = Calendar.getInstance().apply {
            add(Calendar.DATE, dayAhead)
        }
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)
        editText.setOnClickListener {
            DatePickerDialog(context,
                    DatePickerDialog.OnDateSetListener { _, year, month, day ->

                        editText.setText("${(month+1).addZeroIfBelow10()}/${day.addZeroIfBelow10()}/$year")
                    },
                    year, month, day).show()
        }
    }

    override fun focusFirstText() {
        borrowBookInputLayout.requestFocus()
        (context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?)?.
                toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
    }

    override fun clearFocus() {
        (context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?)?.
                hideSoftInputFromWindow(linearContainer.windowToken, 0)
        viewModelInspect.setSelectedItem(viewModelInspect.getSelectedItemLiveData().value)
    }

    override fun tryDeleteCurrentItem() {
        AlertDialog.Builder(context).setTitle("Are you sure want to delete")
                .setPositiveButton("Yes"){ dialog, _ ->
                    super.tryDeleteCurrentItem()
                    dialog.dismiss()
                }
                .setNegativeButton("No"){ dialog, _ ->
                    dialog.dismiss()
                }
                .show()
    }

    override fun deleteCurrentItem() {
        viewModelInspect.getSelectedItemLiveData().value?.let {
            viewModel.deleteCurrent(it){
                if (it.isSuccess) {
                    showLoadingResultToast(it.loadingType)
                    viewModel.shouldFinish.value = true
                } else{
                    showErrorConnectionToast()
                }
            }
            viewModelInspect.editOrCreateMode.value = Pair(false, false)
        }
    }

    override fun tryUpdateCurrentItem() {
        AlertDialog.Builder(context).setTitle("Are you sure want to update?")
                .setPositiveButton("Yes"){ dialog, _ ->
                    super.tryUpdateCurrentItem()
                    dialog.dismiss()
                }
                .setNegativeButton("No"){ dialog, _ ->
                    dialog.dismiss()
                }
                .show()
    }

    override fun updateCurrentItem() {
        createValue()?.let {
            viewModel.updateData(it.apply {
                id = viewModelInspect.getSelectedItemLiveData().value!!.id
            }){
                if (it.isSuccess) {
                    showLoadingResultToast(it.loadingType)
                    viewModel.shouldFinish.value = true
                } else{
                    showErrorConnectionToast()
                }
            }
            viewModelInspect.editOrCreateMode.value = Pair(false, false)
        }
    }
}
