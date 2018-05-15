package com.hsbc.superfielddemo.superfield

import android.util.Log
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import io.reactivex.Observable
import java.util.*

/**
 * Created by zack zeng on 2018/5/11.
 */

enum class ProxyIdEnum {
    FPSID,
    PHONENUMBER,
    SEARCHCOUNTRY,
    EMAIL,
    UNKNOWN
}

class MatchResult {
    var content: String = ""
    var type: ProxyIdEnum = ProxyIdEnum.UNKNOWN
    var resultList: MutableList<Country> = mutableListOf()
}

class SuperFieldMatcher {

    var FPSID_PRIORITY = false
    var HK_PRIORITY = false
    val TAG = "SuperFieldMatcher"

    companion object {
        val instance = SuperFieldMatcher()
    }

    fun parse(country: Country?, content: String): Observable<MatchResult> {
        if (country == null) {
            return parse(content)
        } else {
            val result = MatchResult()
            when (country.codeName) {
                "FPSID" -> {
                    result.type = ProxyIdEnum.FPSID
                    result.content = content
                }
                else -> {
                    result.type = ProxyIdEnum.PHONENUMBER
                    result.content = "+" + country.codeInt + "-" + content

                }
            }
            return Observable.just(result)
        }
    }

    fun parse(content: String): Observable<MatchResult> {
        val result = MatchResult()
        when {
            matchEmail(content) -> {
                Log.e(TAG, "is email $content")
                result.type = ProxyIdEnum.EMAIL
                result.content = content
            }
            matchPhoneNumber(content) -> {
                Log.e(TAG, "is phone number $content")
                matchAccuratePhoneNumber(content, result)
            }
            else -> {
                // do nothing
                result.type = ProxyIdEnum.UNKNOWN
            }
        }

        return Observable.just(result)
    }

    private fun matchAccuratePhoneNumber(content: String, result: MatchResult): Phonenumber.PhoneNumber? {
        if (content.startsWith("852") || content.startsWith("+852")) {
            Log.e(TAG, "hong kong number")
            if (ProxyIdValidator.isValidNewHkMobileNum(content)) {
                val number = content.replaceFirst("852", "").replace("+", "").replace("-", "")
                val phoneNumber = Phonenumber.PhoneNumber()
                phoneNumber.countryCode = 852
                phoneNumber.nationalNumber = number.toLong()

                result.type = ProxyIdEnum.PHONENUMBER
                result.content = getFormattedPhoneNumber(phoneNumber)
                return phoneNumber
            } else {
                result.type = ProxyIdEnum.UNKNOWN
                return null
            }
        } else {
            if (content.startsWith('+')) {
                val phoneNumber = PhoneNumberUtil.getInstance().parse(content, "")
                result.type = ProxyIdEnum.PHONENUMBER
                result.content = getFormattedPhoneNumber(phoneNumber)
                return phoneNumber
            } else if (ProxyIdValidator.isAllNumeric(content)) {
                if (FPSID_PRIORITY && ProxyIdValidator.isFpsId(content)) {
                    result.type = ProxyIdEnum.FPSID
                    result.content = content
                    return null
                }
                if (HK_PRIORITY && content.length == 8) {
                    val phoneNumber = PhoneNumberUtil.getInstance().parse(content, "HK")
                    result.type = ProxyIdEnum.PHONENUMBER
                    result.content = getFormattedPhoneNumber(phoneNumber)
                    return phoneNumber
                } else {
                    // show country code selection list
                    val countryList = searchPhoneNumberCountry(content)
                    if (countryList != null && countryList.size > 0) {
                        result.type = ProxyIdEnum.SEARCHCOUNTRY
                        result.resultList.addAll(countryList)
                        if (content.length == 7) {
                            val country = getFpsIdCountry(content)
                            result.resultList.add(country)
                        }
                    } else if (content.length == 7) {
                        // if match fpsid
                        result.type = ProxyIdEnum.FPSID
                        result.content = content
                    }
                }
            } else {
                val number = content.replace("+", "")
                val numberList = number.split("-")
                var countryCode = ""
                var nationalNumber = ""
                if (numberList.size == 1) {
                    nationalNumber = numberList[0]
                } else if (numberList.size == 2) {
                    countryCode = numberList[0]
                    nationalNumber = numberList[1]
                }
                if (countryCode.isNotBlank()) {
                    val phoneNumber = Phonenumber.PhoneNumber()
                    phoneNumber.countryCode = countryCode.toInt()
                    phoneNumber.nationalNumber = nationalNumber.toLong()

                    result.type = ProxyIdEnum.PHONENUMBER
                    result.content = getFormattedPhoneNumber(phoneNumber)
                    return phoneNumber
                } else {
                    // shouw country code selection list
                    val countryList = searchPhoneNumberCountry(content)
                    if (countryList != null && countryList.size > 0) {
                        result.type = ProxyIdEnum.SEARCHCOUNTRY
                        result.resultList.addAll(countryList)
                        if (content.length == 7) {
                            val country = getFpsIdCountry(content)
                            result.resultList.add(country)
                        }
                    } else if (content.length == 7) {
                        // if match fpsid
                        result.type = ProxyIdEnum.FPSID
                        result.content = content
                    }
                }
            }
        }
        return null
    }

    private fun getFpsIdCountry(content: String): Country {
        return Country("FPSID", "FPSID", content.toInt())
    }

    private fun matchPhoneNumber(content: String): Boolean {
        return ProxyIdValidator.isMayBePhoneNumber(content)
    }

    private fun matchFpsId(content: String): Boolean {
        return ProxyIdValidator.isFpsId(content)
    }

    private fun matchEmail(content: String): Boolean {
        return ProxyIdValidator.isValidEmail(content)
    }

    fun getFormattedPhoneNumber(phoneNumber: Phonenumber.PhoneNumber): String {
        return "+${phoneNumber.countryCode}-${phoneNumber.nationalNumber}"
    }

    private val countryList: MutableList<Country>
        get() {
            return getSupportedCountryList()
        }

    private fun getSupportedCountryList(): MutableList<Country> {
        val supportedRegions = PhoneNumberUtil.getInstance().supportedRegions
        val tmpCountryList: MutableList<Country> = mutableListOf()
        for (supportedRegion in supportedRegions) {
            val countryCode = PhoneNumberUtil.getInstance().getCountryCodeForRegion(supportedRegion)
            val fullName = Locale("en", supportedRegion).getDisplayCountry(Locale.ENGLISH) + "(+$countryCode)"
            tmpCountryList.add(Country(supportedRegion, fullName, countryCode))
        }

        // resort by country full name
        Collections.sort(tmpCountryList, object : Comparator<Country> {
            override fun compare(o1: Country?, o2: Country?): Int {
                if (o1 == null || o2 == null) {
                    return 0
                }
                return o1.fullName.compareTo(o2.fullName)
            }
        })
        return tmpCountryList
    }

    private fun searchPhoneNumberCountry(phone: String): MutableList<Country>? {
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
            return fitCountryList
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
        return null
    }

}