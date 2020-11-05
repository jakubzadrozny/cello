module AND(output out1,  input in1, in2,in3);
  wire w1;
  and (w1, in1, in2);
  and (out1, in3, w1);
endmodule
