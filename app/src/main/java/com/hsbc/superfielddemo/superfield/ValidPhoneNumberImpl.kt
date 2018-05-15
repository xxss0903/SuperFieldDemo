package com.hsbc.superfielddemo.superfield

import com.hsbc.superfielddemo.superfield.ProxyIdValidator
import com.hsbc.superfielddemo.superfield.IValidationStrategy

/**
 * Created by zack zeng on 2018/5/15.
 */
class ValidPhoneNumberImpl: IValidationStrategy {
    override fun validProxyId(input: String):Boolean {
        return ProxyIdValidator.isMayBePhoneNumber(input)
    }

}