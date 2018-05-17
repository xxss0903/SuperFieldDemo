package com.hsbc.superfielddemo.superfield

import android.util.Log
import com.google.i18n.phonenumbers.NumberParseException
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
    var country: String = ""
    var content: String = ""
    var countryCodeInt: Int = -1
    var nationalNumber: String = ""
    var type: ProxyIdEnum = ProxyIdEnum.UNKNOWN
    var resultList: MutableList<Country> = mutableListOf()
}

class SuperFieldMatcher {

    val HK_CODE = 852
    var FPSID_PRIORITY = true
    var HK_PRIORITY = true
    var ONLY_MOBILE_TYPE = true
    val TAG = "SuperFieldMatcher"

    val countryCodeMap: MutableMap<Int, String> = mutableMapOf()
    private val countryList: MutableList<Country>
        get() {
            return getSupportedCountryList()
        }


    companion object {
        val instance = SuperFieldMatcher()
    }

    fun parse0(input: String): Observable<MatchResult> {
        val emailValidator = ProxyIdValidationImpl(ValidEmailImpl())
        val phoneNumberValidator = ProxyIdValidationImpl(ValidPhoneNumberImpl())
        val fpsIdValidator = ProxyIdValidationImpl(ValidFpsidImpl())
        var filterCountryList: MutableList<Country> = mutableListOf()


        val result = MatchResult()
        if (emailValidator.validInput(input)) {
            result.type = ProxyIdEnum.EMAIL
            result.content = input
        } else if (phoneNumberValidator.validInput(input)) {
            if (ProxyIdValidator.isValidNewHkMobileNum(input)) {
//                result.type = ProxyIdEnum.PHONENUMBER
//                result.content = getFormattedHKPHoneNumber(input)
//                val countryCode = countryCodeMap[HK_CODE]
//                if (countryCode != null) {
//                    result.country = getCountryFullName(countryCode)
//                }
                result.type = ProxyIdEnum.SEARCHCOUNTRY
                for (country in countryList) {
                    if (country.codeInt == HK_CODE) {
                        country.phoneNumber = getFormattedHKPHoneNumber(input)
                        filterCountryList.add(country)
                    }
                }
                result.resultList = filterCountryList

            } else if (FPSID_PRIORITY && fpsIdValidator.validInput(input)) {
                result.type = ProxyIdEnum.FPSID
                result.content = input
            } else if (HK_PRIORITY && ProxyIdValidator.isAllNumeric(input) && input.length == 8) {
//                result.type = ProxyIdEnum.PHONENUMBER
//                result.content = getFormattedHKPHoneNumber(input)
//                val countryCode = countryCodeMap[852]
//                if (countryCode != null) {
//                    result.country = getCountryFullName(countryCode)
//                }

                result.type = ProxyIdEnum.SEARCHCOUNTRY
                for (country in countryList) {
                    if (country.codeInt == HK_CODE) {
                        country.phoneNumber = getFormattedHKPHoneNumber(input)
                        filterCountryList.add(country)
                    }
                }
                result.resultList = filterCountryList

            } else if (input.contains("-")) {
                val tmpCountryList = parseWithMinus(input)
                if (tmpCountryList != null && tmpCountryList.size > 0) {
                    Collections.sort(tmpCountryList, object : Comparator<Country> {
                        override fun compare(o1: Country?, o2: Country?): Int {
                            if (o1 == null || o2 == null) {
                                return 0
                            }
                            return o1.fullName.compareTo(o2.fullName)
                        }
                    })
                    result.type = ProxyIdEnum.SEARCHCOUNTRY
                    result.resultList.addAll(tmpCountryList)
                } else {
                    resetMatchResult(result)
                }

//                if (phoneNumber != null) {
//                    result.type = ProxyIdEnum.PHONENUMBER
//                    result.content = getFormattedPhoneNumber(phoneNumber)
//                    val countryCode = countryCodeMap[phoneNumber.countryCode]
//                    if (countryCode != null) {
//                        result.country = getCountryFullName(countryCode)
//                    }
//                }
            } else if (input.startsWith("+")) {
                val tmpCountryList = parseWithPlus(input)
                if (tmpCountryList != null && tmpCountryList.size > 0) {
                    Collections.sort(tmpCountryList, object : Comparator<Country> {
                        override fun compare(o1: Country?, o2: Country?): Int {
                            if (o1 == null || o2 == null) {
                                return 0
                            }
                            return o1.fullName.compareTo(o2.fullName)
                        }
                    })
                    result.type = ProxyIdEnum.SEARCHCOUNTRY
                    result.resultList.addAll(tmpCountryList)
                } else {
                    resetMatchResult(result)
                }
            } else if (ProxyIdValidator.isAllNumeric(input)) {
                val tmpCountryList = parseWithAllNumber(input)

                if (tmpCountryList != null && tmpCountryList.size > 0) {
                    Collections.sort(tmpCountryList, object : Comparator<Country> {
                        override fun compare(o1: Country?, o2: Country?): Int {
                            if (o1 == null || o2 == null) {
                                return 0
                            }
                            return o1.fullName.compareTo(o2.fullName)
                        }
                    })
                    result.type = ProxyIdEnum.SEARCHCOUNTRY
                    result.resultList.addAll(tmpCountryList)
                } else if (ProxyIdValidator.isFpsId(input)) {
                    result.type = ProxyIdEnum.FPSID
                    result.content = input
                } else {
                    resetMatchResult(result)
                }
            }
        } else {
            // unknow type
        }
        return Observable.just(result)
    }

    private fun resetMatchResult(result: MatchResult) {
        result.type = ProxyIdEnum.UNKNOWN
        result.resultList.clear()
        result.country = ""
        result.content = ""
        result.countryCodeInt = -1
        result.nationalNumber = ""
    }

    private fun getFormattedHKPHoneNumber(input: String): String {
        if (input.startsWith("+852")) {
            if (input.contains("-")) {
                return input.replaceFirst("+852", "").replace("-", "")
            } else {
                return input.replace("+852", "")
            }
        }

        if (input.startsWith("852")) {
            if (input.contains("-")) {
                return input.replaceFirst("852-", "")
            } else {
                return input.replaceFirst("852", "")
            }
        }
        return input
    }

    private fun parseWithAllNumber(input: String): MutableList<Country>? {
        var tmpCountryList = searchPhoneNumberCountry(input)
        val content = "+" + input
        var phoneNumber: Phonenumber.PhoneNumber? = null
        try {
            phoneNumber = PhoneNumberUtil.getInstance().parse(content, "")
            for (country in countryList) {
                if (phoneNumber.countryCode == country.codeInt && PhoneNumberUtil.getInstance().isValidNumber(phoneNumber)) {
                    country.phoneNumber = phoneNumber.nationalNumber.toString()
                    tmpCountryList?.add(country)
                }
            }

//            if (phoneNumber != null && PhoneNumberUtil.getInstance().isValidNumber(phoneNumber)) {
//                val region = countryCodeMap[phoneNumber.countryCode]
//                if (region != null) {
//                    val fullName = getCountryFullName(region)
//                    val country = Country(region, fullName, phoneNumber.countryCode)
//                    country.phoneNumber = phoneNumber.nationalNumber.toString()
//                    if (tmpCountryList != null) {
//                        tmpCountryList.add(country)
//                    } else {
//                        tmpCountryList = mutableListOf()
//                        tmpCountryList.add(country)
//                    }
//                }
//            }
            return tmpCountryList
        } catch (e: NumberParseException) {
            e.printStackTrace()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
        return null
    }

    private fun parseWithPlus(input: String): MutableList<Country>? {
        try {
            var tmpCountryList = searchPhoneNumberCountry(input)
            val phoneNumber = PhoneNumberUtil.getInstance().parse(input, "")

            for (country in countryList) {
                if (phoneNumber.countryCode == country.codeInt && PhoneNumberUtil.getInstance().isValidNumber(phoneNumber)) {
                    country.phoneNumber = phoneNumber.nationalNumber.toString()
                    tmpCountryList?.add(country)
                }
            }
            return tmpCountryList


//            if (PhoneNumberUtil.getInstance().isValidNumber(phoneNumber)) {
//                return phoneNumber
//            }
        } catch (e: NumberParseException) {
            e.printStackTrace()
        }
        return null
    }

    private fun parseWithMinus(content: String): MutableList<Country>? {
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
            val tmpCountryList: MutableList<Country> = mutableListOf()
            val countryCodeInt = countryCode.toInt()
            var phoneNumber = Phonenumber.PhoneNumber().setNationalNumber(nationalNumber.toLong()).setCountryCode(countryCodeInt)
            for (country in countryList) {
                if (countryCodeInt == country.codeInt && PhoneNumberUtil.getInstance().isValidNumber(phoneNumber)) {
                    if (ONLY_MOBILE_TYPE && PhoneNumberUtil.getInstance().getNumberType(phoneNumber) == PhoneNumberUtil.PhoneNumberType.MOBILE) {
                        country.phoneNumber = phoneNumber.nationalNumber.toString()
                        tmpCountryList.add(country)
                    }
                    if (!ONLY_MOBILE_TYPE) {
                        country.phoneNumber = phoneNumber.nationalNumber.toString()
                        tmpCountryList.add(country)
                    }
                }
            }
            return tmpCountryList
//            val content = "+" + countryCode + nationalNumber
//            val phoneNumber = PhoneNumberUtil.getInstance().parse(content, "")
//            if (PhoneNumberUtil.getInstance().isValidNumber(phoneNumber)) {
//                result.type = ProxyIdEnum.PHONENUMBER
//                result.content = getFormattedPhoneNumber(phoneNumber)
//                return phoneNumber
//            }
        }
        return null
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
            result.country = country.fullName
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
        try {
            if ((content.startsWith("852") || content.startsWith("+852")) && ProxyIdValidator.isValidNewHkMobileNum(content)) {
                Log.e(TAG, "hong kong number")
                val number = content.replaceFirst("852", "").replace("+", "").replace("-", "")
                val phoneNumber = Phonenumber.PhoneNumber()
                phoneNumber.countryCode = 852
                phoneNumber.nationalNumber = number.toLong()

                result.type = ProxyIdEnum.PHONENUMBER
                result.content = getFormattedPhoneNumber(phoneNumber)
                return phoneNumber
            } else {
                if (ProxyIdValidator.isValidPhoneNumberType1(content)) {
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
                        val phoneNumber = Phonenumber.PhoneNumber()
                        phoneNumber.countryCode = 852
                        phoneNumber.nationalNumber = content.toLong()
//                        val phoneNumber = PhoneNumberUtil.getInstance().parse(content, "HK")
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
                        } else if (ProxyIdValidator.isFpsId(content)) {
                            // if match fpsid
                            result.type = ProxyIdEnum.FPSID
                            result.content = content
                        } else {
                            result.type = ProxyIdEnum.UNKNOWN
                        }
                    }
                } else if (ProxyIdValidator.isValidPhoneNumberType2(content)) {
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
                        } else if (ProxyIdValidator.isFpsId(content)) {
                            // if match fpsid
                            result.type = ProxyIdEnum.FPSID
                            result.content = content
                        }
                    }
                }
            }
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        } catch (e: NumberParseException) {
            e.printStackTrace()
        }
        return null
    }

    private fun getFpsIdCountry(content: String): Country {
        return Country("FPSID", "FPSID", -1)
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

    private fun getSupportedCountryList(): MutableList<Country> {
        val supportedRegions = PhoneNumberUtil.getInstance().supportedRegions
        val tmpCountryList: MutableList<Country> = mutableListOf()
        for (supportedRegion in supportedRegions) {
            val countryCode = PhoneNumberUtil.getInstance().getCountryCodeForRegion(supportedRegion)
            val fullName = getCountryFullName(supportedRegion)
            tmpCountryList.add(Country(supportedRegion, fullName, countryCode))
            countryCodeMap.put(countryCode, supportedRegion)
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

    fun getCountryFullName(region: String): String {
        return Locale("en", region).getDisplayCountry(Locale.ENGLISH)
    }

    private fun searchPhoneNumberCountry(phone: String): MutableList<Country>? {
        try {
            val phoneStr = phone.replace("+", "")
            val phoneNumberLong = phoneStr.toLong()
            val phoneNumber = Phonenumber.PhoneNumber()
            val fitCountryList: MutableList<Country> = mutableListOf()
            for (country in countryList) {
                phoneNumber.nationalNumber = phoneNumberLong
                phoneNumber.countryCode = country.codeInt
                if (ONLY_MOBILE_TYPE) {
                    if (PhoneNumberUtil.getInstance().getNumberType(phoneNumber) == PhoneNumberUtil.PhoneNumberType.MOBILE) {
                        country.phoneNumber = phoneStr
                        fitCountryList.add(country)
                    } else {
                        country.phoneNumber = ""
                    }
                } else {
                    if (PhoneNumberUtil.getInstance().isValidNumber(phoneNumber)) {
                        country.phoneNumber = phoneStr
                        fitCountryList.add(country)
                    } else {
                        country.phoneNumber = ""
                    }
                }
            }
            return fitCountryList
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
        return null
    }

}