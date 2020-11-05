module A(output x,y,z  input a, b, c);
  always@(a,b,c)
    begin
      case({a,b,c})
        3'b000: {x,y,z} = 3'b000;
        3'b001: {x,y,z} = 3'b001;
        3'b010: {x,y,z} = 3'b010;
        3'b011: {x,y,z} = 3'b010;
        3'b100: {x,y,z} = 3'b100;
        3'b101: {x,y,z} = 3'b100;
        3'b110: {x,y,z} = 3'b100;
        3'b111: {x,y,z} = 3'b100;
      endcase
    end
endmodule
