@                                              

@                                                                 

vcpkg install openssl:x64-windows boost-variant:x64-windows boost-system:x64-windows boost-range:x64-windows boost-crc:x64-windows boost-logic:x64-windows boost-parameter:x64-windows boost-asio:x64-windows boost-variant2:x64-windows boost-multi-index:x64-windows boost-multiprecision:x64-windows
@                                                       

choco install swig -y
choco install openssl -y

vcpkg integrate install
