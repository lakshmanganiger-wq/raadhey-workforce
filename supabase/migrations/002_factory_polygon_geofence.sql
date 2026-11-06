-- RAADHEY factory polygon geofence and attendance RPCs
-- Run after 001_initial_schema.sql.
alter table public.workplace_settings add column if not exists boundary_coordinates jsonb;

update public.workplace_settings
set boundary_coordinates='[
 [13.0869225,77.4191348],
 [13.0868676,77.4192857],
 [13.0867285,77.4192381],
 [13.0867945,77.4190815]
]'::jsonb,
latitude=13.0868273605, longitude=77.4191855295,
geofence_radius_m=100, shift_start='09:00', shift_end='18:00',
late_after='09:15', weekly_holiday=0, timezone='Asia/Kolkata', updated_at=now();

create or replace function public.point_in_polygon(p_latitude double precision,p_longitude double precision,p_polygon jsonb)
returns boolean language plpgsql immutable strict set search_path=public as $$
declare n integer; i integer:=0; j integer; yi double precision; yj double precision; xi double precision; xj double precision; inside boolean:=false;
begin
 if jsonb_typeof(p_polygon)<>'array' then return false; end if;
 n:=jsonb_array_length(p_polygon); if n<3 then return false; end if; j:=n-1;
 while i<n loop
  yi:=(p_polygon->i->>0)::double precision; xi:=(p_polygon->i->>1)::double precision;
  yj:=(p_polygon->j->>0)::double precision; xj:=(p_polygon->j->>1)::double precision;
  if ((yi>p_latitude)<>(yj>p_latitude)) and
    (p_longitude<(xj-xi)*(p_latitude-yi)/nullif(yj-yi,0)+xi) then inside:=not inside; end if;
  j:=i; i:=i+1;
 end loop;
 return inside;
end; $$;

create or replace function public.employee_check_in(p_latitude double precision,p_longitude double precision,p_accuracy_m double precision)
returns jsonb language plpgsql security definer set search_path=public as $$
declare s public.workplace_settings%rowtype; v_distance double precision; v_now timestamptz:=now(); v_local timestamp; v_date date; v_status text; v_id uuid;
begin
 if auth.uid() is null then raise exception 'Authentication required'; end if;
 if p_latitude not between -90 and 90 or p_longitude not between -180 and 180 then raise exception 'Invalid GPS coordinates'; end if;
 if p_accuracy_m is null or p_accuracy_m<0 or p_accuracy_m>150 then raise exception 'GPS accuracy is insufficient. Please retry outdoors.'; end if;
 if not exists(select 1 from public.profiles where id=auth.uid() and active=true and role='employee') then raise exception 'Active employee account required'; end if;
 select * into strict s from public.workplace_settings order by updated_at desc limit 1;
 if s.boundary_coordinates is null or jsonb_array_length(s.boundary_coordinates)<3 then raise exception 'RAADHEY factory boundary has not been configured'; end if;
 v_distance:=public.distance_m(s.latitude,s.longitude,p_latitude,p_longitude);
 v_local:=v_now at time zone s.timezone; v_date:=v_local::date;
 if extract(isodow from v_date)=7 then raise exception 'Sunday is the weekly holiday. Attendance check-in is disabled.'; end if;
 if not public.point_in_polygon(p_latitude,p_longitude,s.boundary_coordinates) then
  insert into public.attendance_events(employee_id,event_type,latitude,longitude,accuracy_m,distance_from_workplace_m,outcome,details)
  values(auth.uid(),'rejected_check_in',p_latitude,p_longitude,p_accuracy_m,v_distance,'rejected','GPS point outside RAADHEY factory polygon');
  raise exception 'You are outside the RAADHEY factory boundary. Check-in is allowed only inside the marked premises.';
 end if;
 v_status:=case when v_local::time>s.late_after then 'late' else 'present' end;
 insert into public.attendance(employee_id,work_date,check_in,check_in_lat,check_in_lng,check_in_accuracy_m,check_in_distance_m,status)
 values(auth.uid(),v_date,v_now,p_latitude,p_longitude,p_accuracy_m,v_distance,v_status)
 on conflict(employee_id,work_date) do nothing returning id into v_id;
 if v_id is null then raise exception 'Attendance has already been recorded for today'; end if;
 insert into public.attendance_events(employee_id,attendance_id,event_type,latitude,longitude,accuracy_m,distance_from_workplace_m,outcome)
 values(auth.uid(),v_id,'check_in',p_latitude,p_longitude,p_accuracy_m,v_distance,'accepted');
 return jsonb_build_object('success',true,'attendance_id',v_id,'status',v_status,'checked_in_at',v_now,'inside_factory_boundary',true);
end; $$;

create or replace function public.employee_check_out(p_latitude double precision,p_longitude double precision,p_accuracy_m double precision)
returns jsonb language plpgsql security definer set search_path=public as $$
declare s public.workplace_settings%rowtype; v_distance double precision; v_now timestamptz:=now(); v_date date; v_id uuid; v_checkin timestamptz; v_minutes integer;
begin
 if auth.uid() is null then raise exception 'Authentication required'; end if;
 if p_latitude not between -90 and 90 or p_longitude not between -180 and 180 then raise exception 'Invalid GPS coordinates'; end if;
 if p_accuracy_m is null or p_accuracy_m<0 or p_accuracy_m>150 then raise exception 'GPS accuracy is insufficient. Please retry outdoors.'; end if;
 select * into strict s from public.workplace_settings order by updated_at desc limit 1;
 if s.boundary_coordinates is null or jsonb_array_length(s.boundary_coordinates)<3 then raise exception 'RAADHEY factory boundary has not been configured'; end if;
 v_distance:=public.distance_m(s.latitude,s.longitude,p_latitude,p_longitude); v_date:=(v_now at time zone s.timezone)::date;
 if not public.point_in_polygon(p_latitude,p_longitude,s.boundary_coordinates) then
  insert into public.attendance_events(employee_id,event_type,latitude,longitude,accuracy_m,distance_from_workplace_m,outcome,details)
  values(auth.uid(),'rejected_check_out',p_latitude,p_longitude,p_accuracy_m,v_distance,'rejected','GPS point outside RAADHEY factory polygon');
  raise exception 'You are outside the RAADHEY factory boundary. Check-out must be recorded inside the marked premises.';
 end if;
 select id,check_in into v_id,v_checkin from public.attendance where employee_id=auth.uid() and work_date=v_date and check_out is null for update;
 if v_id is null then raise exception 'No open check-in found for today'; end if;
 v_minutes:=greatest(0,floor(extract(epoch from (v_now-v_checkin))/60)::integer);
 update public.attendance set check_out=v_now,check_out_lat=p_latitude,check_out_lng=p_longitude,check_out_accuracy_m=p_accuracy_m,check_out_distance_m=v_distance,worked_minutes=v_minutes,updated_at=v_now where id=v_id;
 insert into public.attendance_events(employee_id,attendance_id,event_type,latitude,longitude,accuracy_m,distance_from_workplace_m,outcome)
 values(auth.uid(),v_id,'check_out',p_latitude,p_longitude,p_accuracy_m,v_distance,'accepted');
 return jsonb_build_object('success',true,'attendance_id',v_id,'worked_minutes',v_minutes,'checked_out_at',v_now,'inside_factory_boundary',true);
end; $$;

revoke all on function public.point_in_polygon(double precision,double precision,jsonb) from public;
grant execute on function public.point_in_polygon(double precision,double precision,jsonb) to authenticated;
revoke all on function public.employee_check_in(double precision,double precision,double precision) from public;
revoke all on function public.employee_check_out(double precision,double precision,double precision) from public;
grant execute on function public.employee_check_in(double precision,double precision,double precision) to authenticated;
grant execute on function public.employee_check_out(double precision,double precision,double precision) to authenticated;
