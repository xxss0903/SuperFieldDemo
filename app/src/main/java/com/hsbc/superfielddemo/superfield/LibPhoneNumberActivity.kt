package com.hsbc.superfielddemo.superfield

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.InputFilter
import android.text.Spanned
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import com.hsbc.superfielddemo.R
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Function
import io.reactivex.functions.Predicate
import io.reactivex.observers.DisposableObserver
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.activity_libphonenumber.*
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import java.util.regex.Pattern

/**
 * Created by zack zeng on 2018/5/3.
 */
class LibPhoneNumberActivity : AppCompatActivity() {

    val VALID_PROXYID_DURATION: Long = 1
    val REGEX_CHAR = "[0-9a-zA-Z@._+\\-]"
    val TAG = "LibPhoneNumber"

    private var countryRegionList: ArrayList<String> = arrayListOf()
    private val countryList: MutableList<Country> = mutableListOf()
    private var filterCountryList: MutableList<Country> = mutableListOf()
    private val filterCountryFullName: MutableList<String> = mutableListOf()
    private var selectedCountry: Country = Country("HK", "Hong Kong", 582)
    private var isInputByHand = true
    private var popupWindow: PopupWindow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_libphonenumber)

        initDatas()
        initView()
    }

    private fun initDatas() {
        countryRegionList.addAll(PhoneNumberUtil.getInstance().supportedRegions)

        for (supportedRegion in countryRegionList) {
            val countryCode = PhoneNumberUtil.getInstance().getCountryCodeForRegion(supportedRegion)
            val fullName = Locale("en", supportedRegion).getDisplayCountry(Locale.ENGLISH) + "(+$countryCode)"
            countryList.add(Country(supportedRegion, fullName, countryCode))
        }

        // resort by country full name
        Collections.sort(countryList, object : Comparator<Country> {
            override fun compare(o1: Country?, o2: Country?): Int {
                if (o1 == null || o2 == null) {
                    return 0
                }
                return o1.fullName.compareTo(o2.fullName)
            }
        })
    }

    private lateinit var regionAdapter: ArrayAdapter<String>

    private lateinit var superFieldPublishSubject: PublishSubject<String>
    private lateinit var countryPublishSubject: PublishSubject<String>
    private lateinit var phonePublishSubject: PublishSubject<String>
    private lateinit var compositeDisposable: CompositeDisposable

    private val disposableObserver: DisposableObserver<String>
        get() {
            val phoneObserver: DisposableObserver<String> = object : DisposableObserver<String>() {
                override fun onComplete() {

                }

                override fun onNext(t: String?) {
                    if (t == null) {
                        return
                    }
                    parsePhoneNumber(t)
                }

                override fun onError(e: Throwable?) {
                }

            }
            return phoneObserver
        }

    private lateinit var phoneObserver: DisposableObserver<String>

    private fun initView() {

        compositeDisposable = CompositeDisposable()
        countryPublishSubject = PublishSubject.create()
        phonePublishSubject = PublishSubject.create()
        superFieldPublishSubject = PublishSubject.create()

        regionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item)
        regionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sp_countrycode.adapter = regionAdapter
        sp_countrycode.setSelection(countryRegionList.indexOf("CN"))

        btn_parse.setOnClickListener {
            val phoneNumber = et_input.text
            if (phoneNumber.isNotBlank()) {
                parsePhoneNumber(phoneNumber.toString())
            } else {
                Toast.makeText(this, "Please Input Phone Number", Toast.LENGTH_SHORT).show()
            }
        }

        et_input.filters = arrayOf<InputFilter>(object : InputFilter {
            override fun filter(source: CharSequence?, start: Int, end: Int, dest: Spanned?, dstart: Int, dend: Int): CharSequence {
                val pattern = Pattern.compile(REGEX_CHAR)
                val matcher = pattern.matcher(source.toString())
                return if (matcher.find()) {
                    source.toString()
                } else {
                    ""
                }
            }
        })
        et_input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {

            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                dismissCountryCodeList()
                if (isInputByHand && s.toString().isNotBlank()) {
                    startMatchPhoneNumber(s.toString())
                }
                if (s.toString().isBlank()) {
                    enableConfirmButton(false)
                }
            }
        })

        et_country.setOnFocusChangeListener(object : View.OnFocusChangeListener {
            override fun onFocusChange(v: View?, hasFocus: Boolean) {
                dismissCountryCodeList()
            }
        })

        et_country.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.toString().isNotBlank()) {
                    startMatch(s.toString())
                } else {
                    dismissCountryCodeList()
                }
            }
        })

        phoneObserver = object : DisposableObserver<String>() {
            override fun onComplete() {

            }

            override fun onNext(t: String?) {
                if (t == null) {
                    return
                }
                parsePhoneNumber(t)
            }

            override fun onError(e: Throwable?) {
            }

        }

        superFieldPublishSubject.debounce(VALID_PROXYID_DURATION, TimeUnit.SECONDS)
                .filter(object : Predicate<String> {
                    override fun test(t: String): Boolean {
                        Log.d(TAG, "super field fiter")
                        return t.isNotBlank()
                    }
                })
                .switchMap(object : Function<String, Observable<MatchResult>> {
                    override fun apply(t: String): Observable<MatchResult> {
                        return SuperFieldMatcher.instance.parse0(t)
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (ProxyIdValidator.isFpsId(et_input.text.toString())) {
                        enableConfirmButton(true)
                    }
                    when (it.type) {
                        ProxyIdEnum.SEARCHCOUNTRY -> {
                            filterCountryList = it.resultList
                            showCountryCodeList(it)
                        }
                        ProxyIdEnum.UNKNOWN -> {
                            // do nothing
                            showSuperFieldText(it)
                        }
                        else -> {
                            showSuperFieldText(it)
                        }
                    }
                }, {
                    Log.d(TAG, it.message)
                })

        phonePublishSubject.debounce(2, TimeUnit.SECONDS)
                .filter(object : Predicate<String> {
                    override fun test(t: String): Boolean {
                        return t.isNotBlank()
                    }
                })
                .switchMap { t -> Observable.just(t) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(phoneObserver)

        compositeDisposable.add(phoneObserver)
    }

    private fun startMatchPhoneNumber(phoneNumber: String) {
        superFieldPublishSubject.onNext(phoneNumber)
    }

    private fun searchPhoneNumberCountry(phone: String) {
        try {
            val phoneNumberLong = phone.toLong()
            val phoneNumber = Phonenumber.PhoneNumber()
            val fitCountryList: MutableList<Country> = mutableListOf()
            for (country in countryList) {
                phoneNumber.nationalNumber = phoneNumberLong
                phoneNumber.countryCode = country.codeInt
                if (PhoneNumberUtil.getInstance().isValidNumber(phoneNumber)) {
                    fitCountryList.add(country)
                }
            }
            var info = ""
            for (country in fitCountryList) {
                info += "当前号码:${country.fullName}  +${country.codeInt}-$phone" + "\n"
            }
            tv_phone_info.text = info
        } catch (e: NumberFormatException) {
            tv_superfield.text = e.message
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSubscribe()
    }

    private fun startMatch(s: String) {
        countryPublishSubject.onNext(s)
    }

    private fun parsePhoneNumber(phoneNumberStr: String) {
        try {
            if (selectedCountry.codeInt == 852) {
                if (ProxyIdValidator.isValidHKMobileNum(phoneNumberStr)) {
                    val phoneNumber = PhoneNumberUtil.getInstance().parse(phoneNumberStr, selectedCountry.codeName)
                    setPhoneNumber(phoneNumber)
                } else {
                    tv_international_format.text = "Phone Number Wrong."
                }
            } else {
                val phoneNumber = PhoneNumberUtil.getInstance().parse(phoneNumberStr, selectedCountry.codeName)
                if (phoneNumberStr.startsWith('0')) {
                    phoneNumber.numberOfLeadingZeros = 0
                }
                val isValidPhoneNumber = validPhoneNumber(phoneNumber)

                if (isValidPhoneNumber) {
                    setPhoneNumber(phoneNumber)
                } else {
                    tv_international_format.text = "Phone Number Wrong."
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            clearSubscribe()
            compositeDisposable.add(phoneObserver)
            tv_superfield.text = e.message
            tv_international_format.text = "Phone Number Wrong: " + e.message
        }
    }

    private fun setPhoneNumber(phoneNumber: Phonenumber.PhoneNumber) {
        var content = "Phone Number: " + phoneNumber.nationalNumber + "\n" +
                "Country Code: " + selectedCountry.codeInt + "\n" +
                PhoneNumberOfflineGeocoder.getInstance().getDescriptionForValidNumber(phoneNumber, Locale.getDefault())
        if (phoneNumber.hasNumberOfLeadingZeros()) {
            content += "\n +" + selectedCountry.codeInt + " " + phoneNumber.numberOfLeadingZeros + phoneNumber.nationalNumber
        } else {
            content += "\n +" + selectedCountry.codeInt + " " + phoneNumber.nationalNumber
        }
        tv_international_format.text = content
    }

    private fun validPhoneNumber(phoneNumber: Phonenumber.PhoneNumber): Boolean {
        return PhoneNumberUtil.getInstance().isValidNumber(phoneNumber)
    }

    private var rootView: View? = null
    private lateinit var listAdapter: CountryListAdapter

    private fun showCountryCodeList(result: MatchResult) {
        if (popupWindow == null) {
            rootView = LayoutInflater.from(this).inflate(R.layout.countrycode_popupwindow, null)
            val rvCountry = rootView?.findViewById<RecyclerView>(R.id.rv_countrycode)
            rvCountry?.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

            listAdapter = CountryListAdapter()
            listAdapter.setOnItemClickListener(object : OnItemClickListener {
                override fun onClick(position: Int, country: Country) {
                    // choosed country
                    dismissCountryCodeList()
                    SuperFieldMatcher.instance.parse(country, country.phoneNumber)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({
                                showSuperFieldText(it)
                            }, {
                                tv_superfield.text = it.message
                            })
                }
            })
            listAdapter.datas = filterCountryList
            listAdapter.setNationalNumber(result.nationalNumber)
            rvCountry?.adapter = listAdapter
            popupWindow = PopupWindow()
            popupWindow?.contentView = rootView
            popupWindow?.width = 800
            popupWindow?.height = 600
            popupWindow?.showAsDropDown(et_input)
        } else {
            listAdapter.setNationalNumber(result.nationalNumber)
            listAdapter.notifyDataSetChanged()
        }
    }

    private fun showSuperFieldText(result: MatchResult) {
        isInputByHand = false
        when (result.type) {
            ProxyIdEnum.UNKNOWN -> {
                enableConfirmButton(false)
                btn_confirm_proxyid.isEnabled = false
                if (et_input.text.isNotBlank()) {
                    tv_superfield.text = "Unknow Type ${result.content}"
                } else {
                    tv_superfield.text = ""
                }
            }
            ProxyIdEnum.PHONENUMBER -> {
                enableConfirmButton(true)
                val content = result.type.toString() + " # " + result.content
                et_input.setText(result.content)
                tv_superfield.text = content
            }
            else -> {
                enableConfirmButton(true)
                val content = result.type.toString() + " # " + result.content
                tv_superfield.text = content
            }
        }
        isInputByHand = true
    }

    private fun enableConfirmButton(enable: Boolean) {
        btn_confirm_proxyid.isEnabled = enable
        if (!enable) {
            tv_superfield.text = ""
        }
    }

    private fun clearSubscribe() {
        compositeDisposable.clear()
    }

    private fun dismissCountryCodeList() {
        if (popupWindow != null) {
            popupWindow?.dismiss()
            rootView = null
            popupWindow = null
        }
    }


}