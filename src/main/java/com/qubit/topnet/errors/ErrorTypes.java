/*
 * Qurrican
 * Fast HTTP Server Solution.
 * Copyright 2016, Qubit Group <www.qubit.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  
 * If not, see <https://www.gnu.org/licenses/lgpl-3.0.en.html>
 * 
 * Author: Peter Fronc <peter.fronc@qubitdigital.com>
 */

package com.qubit.topnet.errors;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public enum ErrorTypes {
  BAD_CONTENT_HEADER,
  BAD_CONTENT_LENGTH,
  HTTP_NOT_FOUND,
  IO_ERROR,
  HTTP_UNSET_METHOD,
  HTTP_MALFORMED_HEADERS,
  HTTP_UNKNOWN_ERROR,
  HTTP_SERVER_ERROR,
  HTTP_HEADER_TOO_LARGE
}
